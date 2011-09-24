(ns nimrod.conf.loader
 (:use
   [clojure.string :as string :only [split]]
   [clojure.java.io :as io]
   [nimrod.core.history]
   [nimrod.log.tailer])
 (:import [java.util Properties])
 )

(defn- load-logs [props]
  (if-let [logs-property (.getProperty props "nimrod.logs")]
    (loop [logs (string/split logs-property #",")]
      (let [log-data (string/split (first logs) #":")]
        (if (= 2 (count log-data))
          (do
            (start-tailer (log-data 0) (Long/parseLong (log-data 1)))
            (if-let [other-logs (seq (rest logs))]
              (recur other-logs)
              nil
              )
            )
          (throw (IllegalStateException. (str "Bad logs configuration: " log-data)))
          )
        )
      )
    )
  )

(defn- load-history-age [props]
  (if-let [age-property (.getProperty props "nimrod.history.age")]
    (set-default-history-age (Long. age-property))
    )
  )

(defn load-props [source]
  (with-open [stream (io/input-stream source)]
    (let [props (Properties.)]
      (.load props stream)
      (load-logs props)
      (load-history-age props)
      )
    )
  )