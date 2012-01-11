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
        (if (= 3 (count log-data))
          (do
            (start-tailer (log-data 0) (log-data 1) (Long/parseLong (log-data 2)))
            (if-let [other-logs (seq (rest logs))]
              (recur other-logs)
              nil))
          (throw (IllegalStateException. (str "Bad logs configuration: " log-data))))))))

(defn- setup-store [props]
  (let [store-property (or (.getProperty props "nimrod.store") "memory")]
    (condp = store-property
      "memory" (setup-metric-store (new-memory-store))
      "disk" (setup-metric-store (new-disk-store "nimrod-data/db"))
      (throw (IllegalStateException. (str "Bad store configuration: " store-property))))))

(defn setup [source]
  (with-open [stream (io/input-stream source)]
    (let [props (Properties.)]
      (.load props stream)
      (setup-logs props)
      (setup-store props))))