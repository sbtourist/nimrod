(ns nimrod.log.tailer
 (:require
   [clojure.java.io :as io :only [file]]
   [clojure.tools.logging :as log])
 (:use
   [nimrod.core.util]
   [nimrod.log.processor])
 (:import
   [tayler Tailer TailerListenerAdapter]))

(defonce tailer-buffer-size 8192)
(defonce tailers (ref {}))
(defonce tailers-stats (ref {:log-timestamp 0 :processed-logs-per-second 0 :metric-timestamp 0 :processed-metrics-per-second 0}))

(defn update-tail-stats [timestamp-key rate-key]
  (let [now (clock)]
    (dosync
      (if (<= (- now (@tailers-stats timestamp-key)) (seconds 1))
        (let [current-rate (@tailers-stats rate-key)]
          (alter tailers-stats assoc-in [rate-key] (inc current-rate)))
        (do 
          (alter tailers-stats assoc-in [rate-key] 1)
          (alter tailers-stats assoc-in [timestamp-key] now))))))

(defn show-tail-stats []
  (let [now (clock)]
    (when (> (- now (@tailers-stats :log-timestamp)) (seconds 1))
      (dosync
        (alter tailers-stats assoc-in [:processed-logs-per-second] 0)
        (alter tailers-stats assoc-in [:log-timestamp] (clock))))
    (when (> (- now (@tailers-stats :metric-timestamp)) (seconds 1))
      (dosync
        (alter tailers-stats assoc-in [:processed-metrics-per-second] 0)
        (alter tailers-stats assoc-in [:metric-timestamp] (clock))))
    @tailers-stats))

(defn create-tailer [id log interval end]
  (Tailer.
    (io/file log)
    (proxy [TailerListenerAdapter] []
      (handle [obj] 
        (let [processed (process id obj)] 
          (update-tail-stats :log-timestamp :processed-logs-per-second) 
          (when processed (update-tail-stats :metric-timestamp :processed-metrics-per-second))))
      (fileNotFound [] (log/error (str "Log file not found: " log)))
      (fileRotated [] (log/info (str "Rotated log file: " log)))
      (error [obj] (log/error (.getMessage obj) obj))
      (stop [] (log/info (str "Stopped tailing file: " log))))
    interval
    end
    tailer-buffer-size))

(defn start-tailer [id log interval end]
  (let [tailer (create-tailer id log interval end)]
    (if end (log/info (str "Start processing log from end: " log)) (log/info (str "Start processing log from beginning: " log)))
    (dosync
      (if (contains? @tailers id)
        (throw (IllegalStateException. (str "Duplicated log identifier: " id)))
        (alter tailers assoc id {:log log :tailer tailer})))
    (doto 
      (Thread. tailer) (.setDaemon true) (.start))
    id))

(defn stop-tailer [id]
  (let [tailer (ref nil)]
    (dosync
      (if (@tailers id)
        (do
          (ref-set tailer (@tailers id))
          (alter tailers dissoc id))
        (throw (IllegalStateException. (str "No tailer for id: " id)))))
    (if @tailer
      (.stop (@tailer :tailer))
      (log/info (str "Stop listening to log: " (@tailer :log))))))

(defn list-tailers []
  (into (sorted-map)
    (for [tailer @tailers] [(tailer 0) ((tailer 1) :log)])))
