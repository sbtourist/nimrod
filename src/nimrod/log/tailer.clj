(ns nimrod.log.tailer
 (:require
   [clojure.java.io :as io :only [file]]
   [clojure.tools.logging :as log])
 (:use
   [nimrod.log.processor])
 (:import
   [tayler Tailer TailerListenerAdapter]))

(defonce tailer-buffer-size 8192)
(defonce tailers (ref {}))

(defn- create-tailer [id log interval end]
  (Tailer.
    (io/file log)
    (proxy [TailerListenerAdapter] []
      (fileNotFound [] (log/error (str "Log file not found: " log)))
      (fileRotated [] (log/info (str "Rotated log file: " log)))
      (error [obj] (log/error (.getMessage obj) obj))
      (handle [obj] (process id obj))
      (stop [] (log/info (str "Stopped tailing file: " log))))
    interval
    end
    tailer-buffer-size))

(defn start-tailer [id log interval end]
  (let [tailer (create-tailer id log interval end)]
    (if end (log/info (str "Start listening to log: " log)) (log/info (str "Start processing log: " log)))
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