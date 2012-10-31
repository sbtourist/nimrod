(ns nimrod.core.setup
 (:require
   [clojure.java.io :as io])
 (:use
   [nimrod.core.metric]
   [nimrod.core.store]
   [nimrod.log.tailer]
   [nimrod.web.server])
 (:import [com.typesafe.config ConfigFactory]))

(defn- setup-logs [conf]
  (when-let [logs (.get conf "logs")]
    (doseq [log logs] (start-tailer (key log) (.get (val log) "source") (.get (val log) "interval") (.get (val log) "end")))))

(defn- setup-store [conf]
  (let 
    [store (into {"type" "disk"} (.get conf "store"))
    path (io/file (or (.get store "path") ".")  "nimrod-data/db")
    options (into {} (.get store "options"))
    sampling (into {} (.get store "sampling"))]
    (condp = (store "type")
      "disk" (start-store (new-disk-store (.getCanonicalPath path) options sampling))
      (throw (IllegalStateException. (str "Bad store configuration: " (store "type")))))))

(defn- setup-server [conf]
  (when-let [server (.get conf "server")]
    (when (.get server "max-busy-requests") (reset! max-busy-requests (.get server "max-busy-requests")))
    (start-server (.get server "port") (.get server "threads"))))

(defn setup [source]
  (let [conf (.unwrapped (.root (ConfigFactory/parseFile (io/file source))))]
    (setup-store conf)
    (setup-logs conf)
    (setup-server conf)))
