(ns nimrod.conf.setup
 (:use
   [clojure.string :as string :only [split]]
   [clojure.java.io :as io]
   [nimrod.core.metric]
   [nimrod.core.store]
   [nimrod.log.tailer])
 (:import [java.util Properties]))

(defn- setup-logs [props]
  (if-let [logs-property (.getProperty props "nimrod.logs")]
    (loop [logs (string/split logs-property #",")]
      (let [log-data (string/split (first logs) #":")]
        (if (= 2 (count log-data))
          (do
            (start-tailer (log-data 0) (Long/parseLong (log-data 1)))
            (if-let [other-logs (seq (rest logs))]
              (recur other-logs)
              nil))
          (throw (IllegalStateException. (str "Bad logs configuration: " log-data))))))))

(defn- setup-store [props]
  (setup-metric-store (new-memory-store)))

(defn setup [source]
  (with-open [stream (io/input-stream source)]
    (let [props (Properties.)]
      (.load props stream)
      (setup-logs props)
      (setup-store props))))