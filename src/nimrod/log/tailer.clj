(ns nimrod.log.tailer
 (:use
   [clojure.java.io :as io :only [file]]
   [clojure.tools.logging :as log]
   [nimrod.log.processor])
 (:import
   [tayler Tailer TailerListenerAdapter])
 )

(defonce tailer-buffer-size 4096)
(defonce tailers (ref {}))
(defonce tailers-sequence (atom 0))

(defn- create-tailer [id log interval]
  (Tailer.
    (io/file log)
    (proxy [TailerListenerAdapter] []
      (fileNotFound [] (log/error (str "Log file not found: " log)))
      (fileRotated [] (log/info (str "Rotated log file: " log)))
      (error [obj] (log/error (.getMessage obj) obj))
      (handle [obj] (process id obj))
      (stop [] (log/info (str "Stopped tailing file: " log)))
      )
    interval
    true
    tailer-buffer-size
    )
  )

(defn start-tailer [log interval]
  (dosync
    (let [id (Long/toString (swap! tailers-sequence inc)) tailer (create-tailer id log interval)]
      (log/info (str "Start listening to log: " log))
      (doto (Thread. tailer) (.setDaemon true) (.start))
      (alter tailers assoc id {:log log :tailer tailer})
      id
      )
    )
  )

(defn stop-tailer [id]
  (let [tailer (ref nil)]
    (dosync
      (if (@tailers id)
        (do
          (ref-set tailer (@tailers id))
          (alter tailers dissoc id)
          )
        (throw (IllegalStateException. (str "No tailer for id: " id)))
        )
      )
    (if @tailer
      (.stop (@tailer :tailer))
      (log/info (str "Stop listening to log: " (@tailer :log)))
      )
    )
  )

(defn list-tailers []
  (into (sorted-map)
    (for [tailer @tailers] [(tailer 0) ((tailer 1) :log)])
    )
  )