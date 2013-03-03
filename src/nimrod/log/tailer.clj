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

(defonce tailer-buffer-size (* 64 1024))
(defonce tailers (ref {}))

(defn- create-tailer [id log interval end]
  (Tailer.
    (io/file log)
    (proxy [TailerListenerAdapter] []
      (handle [obj] 
        (let [now (clock) processed (process id obj)] 
          (update-rate-stats [:processed-logs-per-second] now (seconds 1))
          (update-rate-stats [:processed-logs-per-second-per-file (keyword id)] now (seconds 1))
          (when processed 
            (do 
              (update-rate-stats [:processed-metrics-per-second] now (seconds 1))
              (update-rate-stats [:processed-metrics-per-second-per-file (keyword id)] now (seconds 1))))))
      (fileNotFound [] (log/error (str "Log file not found: " log)))
      (fileRotated [] (log/info (str "Rotated log file: " log)))
      (error [obj] (log/error (.getMessage obj)))
      (stop [] (log/info (str "Stopped tailing file: " log))))
    interval
    end
    tailer-buffer-size))

(defn start-tailer [id log interval end]
  (let [tailer (create-tailer id log interval end)]
    (if end (log/info (str "Start processing log from end of file: " log)) (log/info (str "Start processing log from start of file: " log)))
    (dosync
      (if (contains? @tailers id)
        (throw (IllegalStateException. (str "Duplicated log identifier: " id)))
        (alter tailers assoc id {:log log :tailer tailer})))
    (doto 
      (Thread. tailer (str "Tailer-" id)) (.setDaemon true) (.start))
    id))

(defn stop-tailer [id]
  (if-let [tailer (@tailers id)]
    (do 
      (.stop (tailer :tailer))
      (log/info (str "Stop processing log: " (tailer :log)))
      true) 
    false))

(defn list-tailers []
  (into (sorted-map)
    (for [tailer @tailers] [(tailer 0) ((tailer 1) :log)])))

(defn show-tail-stats []
  (show-stats 
    (-> [[:processed-logs-per-second] [:processed-metrics-per-second]]
      (into (for [id (keys @tailers)] [:processed-logs-per-second-per-file (keyword id)]))
      (into (for [id (keys @tailers)] [:processed-metrics-per-second-per-file (keyword id)])))
    (clock)
    (seconds 1)))
