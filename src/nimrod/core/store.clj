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
(defonce default-sampling-factor 10)
(defonce default-sampling-frequency 0)

(defonce default-age oneMinute)

(defn- from-json-map [m]
  (json/generate-smile m))

(defn- to-json-map [v]
  (json/parse-smile v true (fn [_] #{})))

(defn- do-sampling? [samples sampling-frequency]
  (and (not (zero? sampling-frequency)) (= samples sampling-frequency)))

(defprotocol Store
  (init [this])
  
  (set-metric [this metric-ns metric-type metric-id metric aggregation])
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
            "CREATE CACHED TABLE metrics (ns LONGVARCHAR, type LONGVARCHAR, id LONGVARCHAR, seq BIGINT, timestamp BIGINT, metric LONGVARBINARY, aggregation DOUBLE, PRIMARY KEY (ns,type,id))")
          (sql/do-prepared 
            "CREATE CACHED TABLE history (ns LONGVARCHAR, type LONGVARCHAR, id LONGVARCHAR, seq BIGINT, timestamp BIGINT, metric LONGVARBINARY, aggregation DOUBLE, PRIMARY KEY (ns,type,id,seq))")
          (sql/do-prepared 
            "CREATE INDEX history_idx ON history (ns,type,id,timestamp)")))
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
              (alter memory assoc-in [(metric :ns) (metric :type) (metric :id) :seq] (metric :seq))
              (alter memory assoc-in [(metric :ns) (metric :type) (metric :id) :metric] (to-json-map (metric :metric)))
              (alter memory assoc-in [(metric :ns) (metric :type) (metric :id) :samples] 0)))))))
  
  (set-metric [this metric-ns metric-type metric-id metric aggregation]
    (let [current-seq-value (or (get-in @memory [metric-ns metric-type metric-id :seq]) 0)
          current-samples (or (get-in @memory [metric-ns metric-type metric-id :samples]) 0)
          new-seq-value (inc current-seq-value)
          new-samples (inc current-samples)
          new-aggregation-value aggregation
          new-json-metric (from-json-map metric)
          new-metric-timestamp (metric :timestamp)
          sampling-factor (or 
                            (sampling (str metric-ns "." metric-type "." metric-id ".factor"))
                            (sampling (str metric-ns "." metric-type ".factor"))
                            (sampling (str metric-ns ".factor")) 
                            default-sampling-factor)
          sampling-frequency (or 
                               (sampling (str metric-ns "." metric-type "." metric-id ".frequency"))
                               (sampling (str metric-ns "." metric-type ".frequency"))
                               (sampling (str metric-ns ".frequency"))
                               default-sampling-frequency)]
      ; Increment sequence prior to actually using it to avoid duplicated entries:
      (dosync
        (alter memory assoc-in [metric-ns metric-type metric-id :seq] new-seq-value))
      ; Insert metric/history:
      (sql/with-connection connection-factory 
        (sql/transaction 
          (sql/update-or-insert-values 
            "metrics"
            ["ns=? AND type=? AND id=?" metric-ns metric-type metric-id]
            {"ns" metric-ns "type" metric-type "id" metric-id "timestamp" new-metric-timestamp "seq" new-seq-value "metric" new-json-metric "aggregation" new-aggregation-value})
          (sql/insert-record
            "history"
            {"ns" metric-ns "type" metric-type "id" metric-id "timestamp" new-metric-timestamp "seq" new-seq-value "metric" new-json-metric "aggregation" new-aggregation-value})))
      (dosync
        (alter memory assoc-in [metric-ns metric-type metric-id :metric] metric))
      ; Optionally sample:
      (sql/with-connection connection-factory 
        (sql/transaction     
          (if (do-sampling? new-samples sampling-frequency)
            (do 
              (loop [record new-seq-value samples new-samples to-delete (- new-samples (/ new-samples sampling-factor))]
                (if ( <= (int (* samples (rand))) to-delete)
                  (do
                    (sql/delete-rows "history" 
                      ["ns=? AND type=? AND id=? AND seq=?"
                       metric-ns metric-type metric-id record])
                    (when (> (dec to-delete) 0) (recur (dec record) (dec samples) (dec to-delete))))
                  (recur (dec record) (dec samples) to-delete)))
              (dosync
                (alter memory assoc-in [metric-ns metric-type metric-id :samples] 0)))
            (dosync
              (alter memory assoc-in [metric-ns metric-type metric-id :samples] new-samples)))))))
  
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
              (let [metrics (into [] (for [metric (map #(to-json-map (%1 :metric)) r) :when (cset/subset? tags (metric :tags))] metric))]
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
                      (str "SELECT metric, aggregation FROM history WHERE ns='" metric-ns "' AND type='" metric-type "' AND id='" metric-id "' AND timestamp>=" actual-from " AND timestamp<=" actual-to " ORDER BY aggregation ASC") 
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
             (percentiles total (aggregators :percentiles) #(when (.absolute rs %1) (to-json-map (.getBytes rs 1))))
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
                                "shutdown=true;hsqldb.applog=1;hsqldb.log_size=50;hsqldb.cache_file_scale=128;"
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