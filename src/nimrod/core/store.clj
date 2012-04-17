(ns nimrod.core.store
 (:require
   [cheshire.core :as json]
   [clojure.java.jdbc.internal :as jdbc]
   [clojure.java.jdbc :as sql]
   [clojure.set :as cset]
   [clojure.tools.logging :as log])
 (:use
   [nimrod.core.stat]
   [nimrod.core.util])
 (:import com.mchange.v2.c3p0.ComboPooledDataSource)
 (:refer-clojure :exclude (resultset-seq)))

(defonce oneDay 86400000)
(defonce oneHour 3600000)
(defonce oneMinute 60000)

(defonce default-cache-entries 1000)
(defonce default-cache-results 1000000)
(defonce default-defrag-limit 0)
(defonce default-sampling-threshold 1)

(defonce default-age oneMinute)

(defn- to-json [v]
  (json/parse-string v true (fn [_] #{})))

(defn- sample? [old-value new-value threshold]
  (not (= (int (/ old-value threshold)) (int (/ new-value threshold)))))

(defprotocol Store
  (init [this])
  
  (set-metric [this metric-ns metric-type metric-id metric raw])
  (remove-metric [this metric-ns metric-type metric-id])
  (read-metric [this metric-ns metric-type metric-id])
  
  (list-metrics [this metric-ns metric-type])
  
  (read-history [this metric-ns metric-type metric-id tags age from to])
  (remove-history [this metric-ns metric-type metric-id age from to])
  (aggregate-history [this metric-ns metric-type metric-id age from to aggregators])
  
  (list-types [this metric-ns]))


(deftype DiskStore [connection-factory memory options sampling]
  
  Store
  
  (init [this]
    (dosync ref-set memory {})
    (try 
      (sql/with-connection connection-factory
        (sql/transaction 
          (sql/do-prepared 
            "CREATE CACHED TABLE metrics (ns LONGVARCHAR, type LONGVARCHAR, id LONGVARCHAR, timestamp BIGINT, raw DOUBLE, metric LONGVARCHAR, PRIMARY KEY (ns,type,id))")
          (sql/do-prepared 
            "CREATE CACHED TABLE history (ns LONGVARCHAR, type LONGVARCHAR, id LONGVARCHAR, timestamp BIGINT, seq BIGINT GENERATED ALWAYS AS IDENTITY, raw DOUBLE, metric LONGVARCHAR, PRIMARY KEY (ns,type,id,timestamp,seq))")))
      (catch Exception ex))
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared "SET DATABASE TRANSACTION CONTROL MVCC"))
      (sql/transaction 
        (sql/do-prepared (str "SET DATABASE DEFAULT RESULT MEMORY ROWS " (or (options "cache.results") default-cache-results))))
      (sql/transaction 
        (sql/with-query-results 
          all-metrics
          ["SELECT * FROM metrics"] 
          (doseq [metric all-metrics] 
            (dosync 
              (alter memory assoc-in [(metric :ns) (metric :type) (metric :id) :raw] (metric :raw))
              (alter memory assoc-in [(metric :ns) (metric :type) (metric :id) :metric] (to-json (metric :metric)))))))))
  
  (set-metric [this metric-ns metric-type metric-id metric raw]
    (let [old-raw-value (get-in @memory [metric-ns metric-type metric-id :raw])
          new-json-metric (json/generate-string metric)
          new-metric-timestamp (metric :timestamp)
          sampling-threshold (or 
                               (sampling metric-ns) 
                               (sampling (str metric-ns "." metric-type))
                               (sampling (str metric-ns "." metric-type "." metric-id))
                               default-sampling-threshold)]
      (sql/with-connection connection-factory 
        (sql/transaction 
          (sql/update-or-insert-values 
            "metrics"
            ["ns=? AND type=? AND id=?" metric-ns metric-type metric-id]
            {"ns" metric-ns "type" metric-type "id" metric-id "timestamp" new-metric-timestamp "raw" raw "metric" new-json-metric})
          (when (or (nil? old-raw-value) (sample? old-raw-value raw sampling-threshold))
            (sql/insert-record
              "history"
              {"ns" metric-ns "type" metric-type "id" metric-id "timestamp" new-metric-timestamp "raw" raw "metric" new-json-metric}))))
      (dosync
        (alter memory assoc-in [metric-ns metric-type metric-id :raw] raw)
        (alter memory assoc-in [metric-ns metric-type metric-id :metric] metric))))
  
  (remove-metric [this metric-ns metric-type metric-id]
    (sql/with-connection connection-factory 
      (sql/transaction 
        (sql/delete-rows 
          "metrics" 
          ["ns=? AND type=? AND id=?" metric-ns metric-type metric-id])))
    (dosync
      (if-let [metrics (get-in @memory [metric-ns metric-type])]
        (alter memory assoc-in [metric-ns metric-type] (dissoc metrics metric-id)))))
  
  (read-metric [this metric-ns metric-type metric-id]
    (get-in @memory [metric-ns metric-type metric-id :metric]))
  
  (list-metrics [this metric-ns metric-type]
    (sql/with-connection connection-factory
      (sql/transaction (sql/with-query-results 
                         r 
                         ["SELECT id FROM metrics WHERE ns=? AND type=? GROUP BY id ORDER BY id" metric-ns metric-type]
                         (when (seq r)
                           (into [] (map #(get %1 :id) r)))))))
  
  (read-history [this metric-ns metric-type metric-id tags age from to]
    (let [actual-from (if (nil? from) (- (System/currentTimeMillis) (or age default-age)) from)
          actual-to (or to Long/MAX_VALUE)]
      (sql/with-connection connection-factory 
        (sql/transaction 
          (sql/with-query-results 
            r 
            ["SELECT metric FROM history WHERE ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<=? ORDER BY timestamp DESC" 
             metric-ns metric-type metric-id actual-from actual-to]
            (when (seq r) 
              (let [metrics (into [] (for [metric (map #(to-json (%1 :metric)) r) :when (cset/subset? tags (metric :tags))] metric))]
                {:time {:from actual-from :to actual-to} :size (count metrics) :values metrics})))))))
  
  (remove-history [this metric-ns metric-type metric-id age from to]
    (let [actual-from (or from 0) 
          actual-to (if (nil? to) (- (System/currentTimeMillis) (or age default-age)) to)]
      (sql/with-connection connection-factory 
        (let [timestamps (sql/transaction 
                           (sql/with-query-results r
                             ["SELECT MIN(timestamp) AS mints, MAX(timestamp) AS maxts FROM history WHERE ns=? AND type=? AND id=?" metric-ns metric-type metric-id]
                             (first r)))
              min-timestamp (max (timestamps :mints) actual-from)
              max-timestamp (min (timestamps :maxts) actual-to)]
          (loop [upbound (min (+ min-timestamp oneDay) max-timestamp)]
            (sql/transaction 
              (sql/delete-rows "history" 
                ["ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<?"
                 metric-ns metric-type metric-id min-timestamp upbound]))
            (when (< upbound max-timestamp)
              (recur (min (+ upbound oneDay) max-timestamp))))))))
  
  (aggregate-history [this metric-ns metric-type metric-id age from to aggregators]
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared "SET DATABASE DEFAULT ISOLATION LEVEL SERIALIZABLE")
        (let [actual-from (if (nil? from) (- (System/currentTimeMillis) (or age default-age)) from)
              actual-to (or to Long/MAX_VALUE)
              total (sql/with-query-results total-results
                      ["SELECT COUNT(*) AS total FROM history WHERE ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<=?" metric-ns metric-type metric-id actual-from actual-to]
                      ((first total-results) :total))
              query (sql/prepare-statement 
                      (sql/connection) 
                      (str "SELECT raw, metric FROM history WHERE ns='" metric-ns "' AND type='" metric-type "' AND id='" metric-id "' AND timestamp>=" actual-from " AND timestamp<=" actual-to " ORDER BY raw ASC") 
                      :concurrency :read-only :result-type :scroll-insensitive :fetch-size 1)
              rs (.executeQuery query)]
          (if (.first rs)
            {:time 
             {:from actual-from :to actual-to}
             :size 
             total
             :median
             (median total #(when (.absolute rs %1) (.getDouble rs 1)))
             :percentiles
             (percentiles total (aggregators :percentiles) #(when (.absolute rs %1) (to-json (.getString rs 2))))
             })))))
  
  (list-types [this metric-ns]
    (sql/with-connection connection-factory
      (sql/transaction
        (sql/with-query-results 
          all-types
          ["SELECT type FROM metrics WHERE ns=? GROUP BY type ORDER BY type" metric-ns]
          (into [] (for [type all-types] (type :type))))))))


(defn new-disk-store 
  ([path options sampling]
    (when (seq options) (log/info (str "Starting DiskStore with options: " options)))
    (when (seq sampling) (log/info (str "Sampling with: " sampling)))
    (let [defrag-limit (or (options "defrag.limit") default-defrag-limit)
          cache-entries (or (options "cache.entries") default-cache-entries)
          pool (doto (ComboPooledDataSource.)
                 (.setDriverClass "org.hsqldb.jdbc.JDBCDriver") 
                 (.setJdbcUrl (str 
                                "jdbc:hsqldb:file:" path ";"
                                "shutdown=true;hsqldb.applog=1;hsqldb.log_size=10;hsqldb.cache_file_scale=128;"
                                "hsqldb.defrag_limit=" defrag-limit ";" 
                                "hsqldb.cache_rows=" cache-entries ";" 
                                "hsqldb.cache_size=" cache-entries))
                 (.setUser "SA")
                 (.setPassword "")
                 (.setMinPoolSize 1)
                 (.setMaxPoolSize 10)
                 (.setInitialPoolSize 0)
                 (.setAcquireIncrement 1)
                 (.setNumHelperThreads 5))
          store (DiskStore. {:datasource pool} (ref {}) options sampling)]
      (.addShutdownHook (Runtime/getRuntime) (proxy [Thread] [] (run [] (.close pool))))
      (init store)
      store))
  ([path options] (new-disk-store path options {}))
  ([path] (new-disk-store path {} {})))