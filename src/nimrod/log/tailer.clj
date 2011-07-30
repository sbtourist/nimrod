(ns nimrod.log.tailer
 (:use
   [clojure.contrib.io :as io]
   [clojure.contrib.logging :as log]
   [nimrod.log.processor])
 (:import
   [nimrod.org.apache.commons.io.input Tailer]
   [org.apache.commons.io.input TailerListenerAdapter])
 (:refer-clojure :exclude [spit])
 )

(defonce tailer-buffer-size 4096)
(defonce tailers (ref {}))
(defonce tailers-sequence (atom 0))

(defn- create-tailer [id log interval]
  (Tailer/create
    (io/file log)
    (proxy [TailerListenerAdapter] []
      (fileNotFound [] (log/error (str "Log file not found: " log)))
      (fileRotated [] (log/info (str "Rotated log file: " log)))
      (handle [obj] (if (string? obj) (process id obj) (log/error (.getMessage obj) obj)))
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