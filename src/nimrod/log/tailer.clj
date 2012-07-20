(ns nimrod.log.tailer
 (:require
   [clojure.java.io :as io :only [file]]
   [clojure.tools.logging :as log])
 (:use
   [nimrod.core.util]
   [nimrod.internal.stats]
   [nimrod.log.processor])
 (:import
   [tayler Tailer TailerListenerAdapter]))

(defonce tailer-buffer-size 8192)
(defonce tailers (ref {}))

(defn- create-tailer [id log interval end]
  (Tailer.
    (io/file log)
    (proxy [TailerListenerAdapter] []
      (handle [obj] 
        (let [now (clock)
              processed (process id obj)] 
          (update-rate-stats :processed-logs-per-second now (seconds 1)) 
          (when processed (update-rate-stats :processed-metrics-per-second now (seconds 1)))))
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

(defn show-tail-stats []
  (refresh-rate-stats :processed-logs-per-second (seconds 1))
  (refresh-rate-stats :processed-metrics-per-second (seconds 1))
  (select-keys (show-stats) [:processed-logs-per-second :processed-metrics-per-second]))
