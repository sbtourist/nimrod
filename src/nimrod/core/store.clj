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

(defonce default-age oneMinute)

(defn- row-to-json [row]
  (json/parse-string (row :metric) true (fn [_] #{})))


(defprotocol Store
  (init [this])
  
  (set-metric [this metric-ns metric-type metric-id metric primary])
  (remove-metric [this metric-ns metric-type metric-id])
  (read-metric [this metric-ns metric-type metric-id])
  
  (list-metrics [this metric-ns metric-type])
  
  (read-history [this metric-ns metric-type metric-id tags age from to])
  (merge-history [this metric-ns metric-type tags age from to])
  
  (remove-history [this metric-ns metric-type metric-id age from to])
  (aggregate-history [this metric-ns metric-type metric-id age from to options])
  
  (list-types [this metric-ns]))


(deftype DiskStore [connection-factory memory options]
  
  Store
  
  (init [this]
    (dosync ref-set memory {})
    (try 
      (sql/with-connection connection-factory
        (sql/transaction 
          (sql/do-prepared 
            "CREATE CACHED TABLE metrics (ns LONGVARCHAR, type LONGVARCHAR, id LONGVARCHAR, timestamp BIGINT, seq BIGINT GENERATED ALWAYS AS IDENTITY, primary_value DOUBLE, metric LONGVARCHAR, PRIMARY KEY (ns,type,id,timestamp,seq))")))
      (catch Exception ex))
    (try 
      (sql/with-connection connection-factory
        (sql/transaction 
          (sql/do-prepared 
            "CREATE INDEX id_idx ON metrics (id)")))
      (catch Exception ex))
    (try 
      (sql/with-connection connection-factory
        (sql/transaction 
          (sql/do-prepared 
            "CREATE INDEX timestamp_idx ON metrics (timestamp)")))
      (catch Exception ex))
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared "SET DATABASE TRANSACTION CONTROL MVCC"))
      (sql/transaction 
        (sql/do-prepared (str "SET DATABASE DEFAULT RESULT MEMORY ROWS " (or (options :cache.results) default-cache-results))))))
  
  (set-metric [this metric-ns metric-type metric-id metric primary]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/insert-record 
                         "metrics"
                         {"ns" metric-ns "type" metric-type "id" metric-id "timestamp" (metric :timestamp) "primary_value" primary "metric" (json/generate-string metric)})))
    (dosync
      (alter memory assoc-in [metric-ns metric-type metric-id] metric)))
  
  (remove-metric [this metric-ns metric-type metric-id]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/delete-rows 
                         "metrics" 
                         ["ns=? AND type=? AND id=?" metric-ns metric-type metric-id])))
    (dosync
      (if-let [metrics (get-in @memory [metric-ns metric-type])]
        (alter memory assoc-in [metric-ns metric-type] (dissoc metrics metric-id)))))
  
  (read-metric [this metric-ns metric-type metric-id]
    (if-let [latest-metric-value (get-in @memory [metric-ns metric-type metric-id])]
      latest-metric-value
      (when-let [latest-metric-value 
                 (sql/with-connection connection-factory
                   (sql/transaction 
                     (sql/with-query-results 
                       latest-metric-values
                       ["SELECT metric FROM metrics WHERE ns=? AND type=? AND id=? ORDER BY timestamp DESC LIMIT 1 USING INDEX" metric-ns metric-type metric-id] 
                       (when (seq latest-metric-values) (row-to-json (first latest-metric-values))))))]
        (dosync 
          (alter memory assoc-in [metric-ns metric-type metric-id] latest-metric-value) 
          latest-metric-value))))
  
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
        (sql/transaction (sql/with-query-results 
                           r 
                           ["SELECT metric FROM metrics WHERE ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<=? ORDER BY timestamp DESC" 
                            metric-ns metric-type metric-id actual-from actual-to]
                           (when (seq r) 
                             (let [metrics (into [] (for [metric (map row-to-json r) :when (cset/subset? tags (metric :tags))] metric))]
                               {:time {:from actual-from :to actual-to} :size (count metrics) :values metrics})))))))
  
  (merge-history [this metric-ns metric-type tags age from to]
    (let [actual-from (if (nil? from) (- (System/currentTimeMillis) (or age default-age)) from)
          actual-to (or to Long/MAX_VALUE)]
      (sql/with-connection connection-factory 
        (sql/transaction (sql/with-query-results 
                           r 
                           ["SELECT metric FROM metrics WHERE ns=? AND type=? AND timestamp>=? AND timestamp<=? ORDER BY timestamp DESC"
                            metric-ns metric-type actual-from actual-to]
                           (when (seq r)
                             (let [metrics (into [] (for [metric (map row-to-json r) :when (cset/subset? tags (metric :tags))] metric))]
                               {:time {:from actual-from :to actual-to} :size (count metrics) :values metrics})))))))
  
  (remove-history [this metric-ns metric-type metric-id age from to]
    (let [actual-from (or from 0) 
          actual-to (if (nil? to) (- (System/currentTimeMillis) (or age default-age)) to)]
      (sql/with-connection connection-factory 
        (let [timestamps (sql/transaction 
                           (sql/with-query-results r
                             ["SELECT MIN(timestamp) AS mints, MAX(timestamp) AS maxts FROM metrics WHERE ns=? AND type=? AND id=?" metric-ns metric-type metric-id]
                             (first r)))
              min-timestamp (max (timestamps :mints) actual-from)
              max-timestamp (min (timestamps :maxts) actual-to)]
          (loop [upbound (min (+ min-timestamp oneDay) max-timestamp)]
            (sql/transaction 
              (sql/delete-rows "metrics" 
                ["ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<?"
                 metric-ns metric-type metric-id min-timestamp upbound]))
            (when (< upbound max-timestamp)
              (recur (min (+ upbound 3600000) max-timestamp))))))))
  
  (aggregate-history [this metric-ns metric-type metric-id age from to options]
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared "SET DATABASE DEFAULT ISOLATION LEVEL SERIALIZABLE")
        (let [actual-from (if (nil? from) (- (System/currentTimeMillis) (or age default-age)) from)
              actual-to (or to Long/MAX_VALUE)
              total (sql/with-query-results total-results
                      ["SELECT COUNT(*) AS total FROM metrics WHERE ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<=?" metric-ns metric-type metric-id actual-from actual-to]
                      ((first total-results) :total))
              query (sql/prepare-statement 
                      (sql/connection) 
                      (str "SELECT metric, primary_value FROM metrics WHERE ns='" metric-ns "' AND type='" metric-type "' AND id='" metric-id "' AND timestamp>=" actual-from " AND timestamp<=" actual-to " ORDER BY primary_value ASC") 
                      :concurrency :read-only :result-type :scroll-insensitive :fetch-size 1)
              rs (.executeQuery query)]
          (if (.first rs)
            {:time 
             {:from actual-from :to actual-to}
             :size 
             total
             :median
             (median total #(when (.absolute rs %1) (.getDouble rs 2)))
             :percentiles
             (percentiles total (options :percentiles) #(when (.absolute rs %1) (json/parse-string (.getString rs 1) true (fn [_] #{}))))
             })))))
  
  (list-types [this metric-ns]
    (sql/with-connection connection-factory
      (sql/transaction
        (sql/with-query-results 
          all-types
          ["SELECT type FROM metrics WHERE ns=? GROUP BY type ORDER BY type" metric-ns]
          (into [] (for [type all-types] (type :type))))))))


(defn new-disk-store [path & options] 
  (let [options (into {} options)
        defrag-limit (or (options :defrag.limit) default-defrag-limit)
        cache-entries (or (options :cache.entries) default-cache-entries)
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
        store (DiskStore. {:datasource pool} (ref {}) options)]
    (log/info (str "Starting DiskStore with options: " options))
    (.addShutdownHook (Runtime/getRuntime) (proxy [Thread] [] (run [] (.close pool))))
    (init store)
    store))