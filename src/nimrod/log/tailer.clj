(ns nimrod.log.tailer
 (:use
   [clojure.contrib.io :as io]
   [clojure.contrib.logging :as log]
   [nimrod.core.util]
   [nimrod.log.processor])
 (:import
   [org.apache.commons.io.input Tailer TailerListenerAdapter])
 (:refer-clojure :exclude [spit])
 )

(defonce tailers (ref {}))
(defonce tailers-sequence (atom 0))

(defn- create-tailer [id log interval metrics]
  (Tailer/create
    (io/file log)
    (proxy [TailerListenerAdapter] []
      (fileNotFound [] (log/error (str "Log file not found: " log)))
      (fileRotated [] (log/info (str "Rotated log file: " log)))
      (handle [obj] (if (string? obj) (process id obj metrics) (log/error (.getMessage obj) obj)))
      )
    interval
    true
    )
  )

(defn start-tailer [log interval metrics]
  (dosync
    (let [id (Long/toString (swap! tailers-sequence inc)) tailer (new-agent (create-tailer id log interval metrics))]
      (alter tailers assoc id {:log log :tailer tailer})
      id
      )
    )
  )

(defn stop-tailer [id]
  (dosync
    (if-let [tailer (get @tailers id)]
      (do
        (alter tailers dissoc id)
        (send-off (tailer :tailer) #(.stop %1))
        )
      (throw (IllegalStateException. (str "No tailer for id: " id)))
      )
    )
  )

(defn list-tailers []
  (into {}
    (for [tailer @tailers] [(tailer 0) ((tailer 1) :log)])
    )
  )