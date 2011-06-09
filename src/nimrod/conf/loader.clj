(ns nimrod.conf.loader
 (:use
   [clojure.string :as string :only [split]]
   [clojure.contrib.properties :as props]
   [nimrod.log.tailer])
 )

(defn load-props [source]
  (let [props (props/read-properties source)]
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
      nil
      )
    )
  )