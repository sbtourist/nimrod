(ns nimrod.core.store
 (:use
   [nimrod.core.util]
   [cheshire.core :as json]
   [clojure.java.jdbc.internal :as jdbc]
   [clojure.java.jdbc :as sql]
   [clojure.set :as cset]
   [clojure.tools.logging :as log])
 (:import com.mchange.v2.c3p0.ComboPooledDataSource)
 (:refer-clojure :exclude  (resultset-seq)))

(defonce default-age Long/MAX_VALUE)
(defonce default-limit 1000)

(defprotocol Store
  (init [this])
  (set-metric [this metric-ns metric-type metric-id metric])
  (remove-metric [this metric-ns metric-type metric-id])
  (remove-metrics [this metric-ns metric-type metric-id age])
  (read-metric [this metric-ns metric-type metric-id])
  (read-metrics [this metric-ns metric-type metric-id tags age limit])
  (list-metrics [this metric-ns metric-type])
  (list-types [this metric-ns]))

(deftype MemoryStore [store]
  Store
  (init [this] nil)
  (set-metric [this metric-ns metric-type metric-id metric]
    (dosync
      (if (get-in @store [metric-ns metric-type metric-id])
        (do
          (alter store assoc-in [metric-ns metric-type metric-id :current] metric)
          (alter store assoc-in [metric-ns metric-type metric-id :history (metric :timestamp)] (unrationalize metric)))
        (let [new-metric {:current metric :history (sorted-map-by > (metric :timestamp) (unrationalize metric))}]
          (alter store assoc-in [metric-ns metric-type metric-id] new-metric))))
    nil)
  (remove-metric [this metric-ns metric-type metric-id]
    (dosync
      (when-let [metrics (get-in @store [metric-ns metric-type])]
        (alter store assoc-in [metric-ns metric-type] (dissoc metrics metric-id))))
    nil)
  (remove-metrics [this metric-ns metric-type metric-id age]
    (dosync
      (when-let [history (get-in @store [metric-ns metric-type metric-id :history])]
        (let [now (System/currentTimeMillis)
              new-history (into (sorted-map-by >) (for [metric history :while (<= (- now ((val metric) :timestamp)) age)] metric))]
          (alter store assoc-in [metric-ns metric-type metric-id :history] new-history))))
    nil)
  (read-metric [this metric-ns metric-type metric-id]
    (if-let [current (get-in @store [metric-ns metric-type metric-id :current])]
      current
      nil))
  (read-metrics [this metric-ns metric-type metric-id tags age limit]
    (if-let [history (get-in @store [metric-ns metric-type metric-id :history])]
      (let [now (System/currentTimeMillis)
            metrics (into [] 
                      (for [metric (take (or limit default-limit) (vals history)) 
                            :while (<= (- now (metric :timestamp)) (or age default-age)) :when (cset/subset? tags (metric :tags))] 
                        metric))]
        (if (seq metrics) metrics nil))
      nil))
  (list-metrics [this metric-ns metric-type]
    (if-let [metrics (get-in @store [metric-ns metric-type])]
      (into [] (sort (keys metrics)))
      nil))
  (list-types [this metric-ns]
    (if-let [types-in-ns (@store metric-ns)]
      (into [] (for [type-with-metrics types-in-ns :when (seq (val type-with-metrics))] (key type-with-metrics)))
      nil)))

(deftype DiskStore [connection-factory memory]
  Store
  (init [this]
    (dosync ref-set memory {})
    (try 
      (sql/with-connection connection-factory
        (sql/transaction 
          (sql/do-prepared 
            "CREATE CACHED TABLE metrics (ns LONGVARCHAR, type LONGVARCHAR, id LONGVARCHAR, timestamp BIGINT, metric LONGVARCHAR, PRIMARY KEY (ns,type,id,timestamp))")))
      (catch Exception ex))
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared 
          "SET DATABASE TRANSACTION CONTROL MVCC")))
    (sql/with-connection connection-factory
      (sql/with-query-results 
        all-metrics
        ["SELECT DISTINCT ns, type, id FROM metrics"] 
        (doseq [metric all-metrics] 
          (let [latest-metric-value 
                (sql/with-query-results 
                  latest-metric-values
                  ["SELECT metric FROM metrics WHERE ns=? AND type=? AND id=? ORDER BY timestamp DESC LIMIT 1" (metric :ns) (metric :type) (metric :id)] 
                  (json/parse-string ((first latest-metric-values) :metric) true (fn [_] #{})))]
            (dosync (alter memory assoc-in [(metric :ns) (metric :type) (metric :id)] latest-metric-value)))))))
  (set-metric [this metric-ns metric-type metric-id metric]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/update-or-insert-values 
                         "metrics"
                         ["ns=? AND type=? AND id=? AND timestamp=?" metric-ns metric-type metric-id (metric :timestamp)]
                         {"ns" metric-ns "type" metric-type "id" metric-id "timestamp" (metric :timestamp) "metric" (json/generate-string metric)})))
    (dosync
      (alter memory assoc-in [metric-ns metric-type metric-id] metric))
    nil)
  (remove-metric [this metric-ns metric-type metric-id]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/delete-rows 
                         "metrics" 
                         ["ns=? AND type=? AND id=?" metric-ns metric-type metric-id])))
    (dosync
      (if-let [metrics (get-in @memory [metric-ns metric-type])]
        (alter memory assoc-in [metric-ns metric-type] (dissoc metrics metric-id))))
    nil)
  (remove-metrics [this metric-ns metric-type metric-id age]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/delete-rows 
                         "metrics" 
                         ["ns=? AND type=? AND id=? AND timestamp<=?" metric-ns metric-type metric-id (- (System/currentTimeMillis) age)])))
    nil)
  (read-metric [this metric-ns metric-type metric-id]
    (get-in @memory [metric-ns metric-type metric-id]))
  (read-metrics [this metric-ns metric-type metric-id tags age limit]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/with-query-results 
                         r 
                         ["SELECT metric FROM metrics WHERE ns=? AND type=? AND id=? AND timestamp>=? ORDER BY timestamp DESC LIMIT ?" 
                          metric-ns metric-type metric-id (- (System/currentTimeMillis) (or age default-age)) (or limit default-limit)]
                         (if (seq r) 
                           (into [] (for [metric (map #(json/parse-string (%1 :metric) true (fn [_] #{})) r) :when (cset/subset? tags (metric :tags))] metric))
                           nil)))))
  (list-metrics [this metric-ns metric-type]
    (sql/with-connection connection-factory
      (sql/transaction (sql/with-query-results 
                         r 
                         ["SELECT DISTINCT id FROM metrics WHERE ns=? AND type=? ORDER BY id" metric-ns metric-type]
                         (if (seq r)
                           (into [] (map #(get %1 :id) r))
                           nil)))))
  (list-types [this metric-ns]
    (if-let [types-in-ns (@memory metric-ns)]
      (into [] (for [type-with-metrics types-in-ns :when (seq (val type-with-metrics))] (key type-with-metrics)))
      nil)))

(defn new-memory-store [] 
  (let [store (MemoryStore. (ref {}))]
    (init store)
    store))

(defn new-disk-store [path] 
  (let [pool (doto (ComboPooledDataSource.)
               (.setDriverClass "org.hsqldb.jdbc.JDBCDriver") 
               (.setJdbcUrl (str "jdbc:hsqldb:file:" path))
               (.setUser "SA")
               (.setPassword "")) 
        store (DiskStore. {:datasource pool} (ref {}))]
    (init store)
    store))