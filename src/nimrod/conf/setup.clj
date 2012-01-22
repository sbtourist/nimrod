(ns nimrod.conf.setup
 (:use
   [clojure.java.io :as io]
   [nimrod.core.metric]
   [nimrod.core.store]
   [nimrod.log.tailer])
 (:import [com.typesafe.config ConfigFactory]))

(defn- setup-logs [conf]
  (when-let [logs (.get conf "logs")]
    (doseq [log logs] (start-tailer (key log) (.get (val log) "source") (.get (val log) "interval")))))

(defn- setup-store [conf]
  (let [store (or (.get conf "store") {"type" "memory"})]
    (let [type (.get store "type")]
      (condp = type
        "memory" (setup-metric-store (new-memory-store))
        "disk" (setup-metric-store (new-disk-store "nimrod-data/db"))
        (throw (IllegalStateException. (str "Bad store configuration: " type)))))))

(defn setup [source]
  (let [conf (.unwrapped (.root (ConfigFactory/parseFile (io/file source))))]
    (setup-logs conf)
    (setup-store conf)))
 
 
 
; (:use
;   [clojure.java.io :as io]
;   [clojure.tools.logging :as log]
;   [nimrod.core.metric]
;   [nimrod.core.store]
;   [nimrod.log.tailer])
; (:import [com.typesafe.config ConfigFactory]))
;
;(defn- javamap-to-clojuremap [m]
;  (into {} 
;    (for [v m]
;      (condp instance? (val v)
;        java.util.Map 
;        java.util.List))))
;
;(defn- setup-logs [conf]
;  (when-let [logs (conf "logs")]
;    (doseq [log logs] (start-tailer (key log) ((val log) "source") ((val log) "interval")))))
;
;(defn- setup-store [conf]
;  (let [store (or (conf "store") {"type" "memory"})]
;    (let [type (store "type")]
;      (condp = type
;        "memory" (setup-metric-store (new-memory-store))
;        "disk" (setup-metric-store (new-disk-store "nimrod-data/db"))
;        (throw (IllegalStateException. (str "Bad store configuration: " type)))))))
;
;(defn setup [source]
;  (let [conf (.unwrapped (.root (ConfigFactory/parseFile (io/file source))))]
;    (setup-logs conf)
;    (setup-store conf)))