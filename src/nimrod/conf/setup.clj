(ns nimrod.conf.setup
 (:use
   [clojure.string :as string :only [split]]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [cheshire.core :as json]
   [nimrod.core.metric]
   [nimrod.core.store]
   [nimrod.log.tailer]))

(defn- setup-logs [logs]
  (doseq [log logs]
    (if-let [id (key log)]
      (if-let [path ((val log) "path")]
        (if-let [interval ((val log) "interval")]
          (start-tailer id path interval)
          (throw (IllegalStateException. (str "No interval specified for log: " log))))
        (throw (IllegalStateException. (str "No path specified for log: " log))))
      (throw (IllegalStateException. (str "No id specified for log: " log))))))

(defn- setup-store [store]
  (let [type (or (store "type") "memory")]
    (condp = type
      "memory" (setup-metric-store (new-memory-store))
      "disk" (setup-metric-store (new-disk-store "nimrod-data/db"))
      (throw (IllegalStateException. (str "Bad store configuration: " store))))))

(defn setup [source]
    (with-open [stream (io/reader source)]
      (let [config (json/parse-stream stream)]
        (setup-logs (or (config "logs") {}))
        (setup-store (or (config "store") {})))))