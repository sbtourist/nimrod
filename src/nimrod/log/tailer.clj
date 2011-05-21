(ns nimrod.log.tailer
 (:use
   [clojure.contrib.io :as io]
   [clojure.contrib.logging :as log]
   [nimrod.core.metrics]
   [nimrod.core.util]
   [nimrod.log.processor])
 (:import
   [org.apache.commons.io.input Tailer TailerListenerAdapter])
 (:refer-clojure :exclude [spit])
 )

(defonce tailers (ref {}))
(defonce tail-interval 10000)

(defn- create-tailer
  ([log]
    (create-tailer log tail-interval)
    )
  ([log interval]
    (Tailer/create
      (io/file log)
      (proxy [TailerListenerAdapter] []
        (fileNotFound [] (log/error (str "Log file not found: " log)))
        (fileRotated [] (log/info (str "Rotated log file: " log)))
        (handle [obj] (if (string? obj) (process obj metrics) (log/error (.getMessage obj) obj)))
        )
      interval
      true
      )
    )
  )

(defn start-tailer
  ([log]
    (start-tailer log tail-interval)
    )
  ([log interval]
    (dosync
      (if-let [tailer (get @tailers log)]
        (throw (IllegalStateException. (str "Already processing log: " log)))
        (let [tailer (new-agent (create-tailer log interval))]
          (alter tailers assoc log tailer)
          )
        )
      )
    )
  )

(defn stop-tailer [log]
  (dosync
    (if-let [tailer (get @tailers log)]
      (do
        (alter tailers dissoc log)
        (send-off tailer #(.stop %1))
        )
      (throw (IllegalStateException. (str "No tailer for log: " log)))
      )
    )
  )