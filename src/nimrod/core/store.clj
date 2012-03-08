(ns nimrod.core.store
 (:use
   [nimrod.core.stat]
   [nimrod.core.util]
   [cheshire.core :as json]
   [clojure.java.jdbc.internal :as jdbc]
   [clojure.java.jdbc :as sql]
   [clojure.set :as cset]
   [clojure.tools.logging :as log])
 (:import com.mchange.v2.c3p0.ComboPooledDataSource)
 (:refer-clojure :exclude (resultset-seq)))

(defonce default-age Long/MAX_VALUE)
(defonce default-limit 1000)

(defn- row-to-json [row]
  (json/parse-string (row :metric) true (fn [_] #{})))


(defprotocol Store
  (init [this])
  (set-metric [this metric-ns metric-type metric-id metric primary])
  (remove-metric [this metric-ns metric-type metric-id])
  (read-metric [this metric-ns metric-type metric-id])
  (list-metrics [this metric-ns metric-type])
  (read-history [this metric-ns metric-type metric-id tags age limit])
  (remove-history [this metric-ns metric-type metric-id age] [this metric-ns metric-type age])
  (merge-history [this metric-ns metric-type tags age limit])
  (aggregate-history [this metric-ns metric-type metric-id from to options])
  (list-types [this metric-ns]))


(deftype MemoryStore [store]
  
  Store
  
  (init [this] nil)
  
  (set-metric [this metric-ns metric-type metric-id metric _ignored]
    (dosync
      (if (get-in @store [metric-ns metric-type metric-id])
        (do
          (alter store assoc-in [metric-ns metric-type metric-id :current] metric)
          (alter store assoc-in [metric-ns metric-type metric-id :history (metric :timestamp)] metric))
        (let [new-metric {:current metric :history (sorted-map-by > (metric :timestamp) metric)}]
          (alter store assoc-in [metric-ns metric-type metric-id] new-metric))))
    nil)
  
  (remove-metric [this metric-ns metric-type metric-id]
    (dosync
      (when-let [metrics (get-in @store [metric-ns metric-type])]
        (alter store assoc-in [metric-ns metric-type] (dissoc metrics metric-id))))
    nil)
  
  (read-metric [this metric-ns metric-type metric-id]
    (if-let [current (get-in @store [metric-ns metric-type metric-id :current])]
      current
      nil))
  
  (list-metrics [this metric-ns metric-type]
    (if-let [metrics-by-id (get-in @store [metric-ns metric-type])]
      (into [] (sort (keys metrics-by-id)))
      nil))
  
  (read-history [this metric-ns metric-type metric-id tags age limit]
    (if-let [history (get-in @store [metric-ns metric-type metric-id :history])]
      (let [now (System/currentTimeMillis)
            actual-limit (or limit default-limit)
            metrics (into [] 
                      (for [metric (take actual-limit (vals history)) 
                            :while (<= (- now (metric :timestamp)) (or age default-age)) :when (cset/subset? tags (metric :tags))] 
                        metric))]
        (if (seq metrics) 
          {:size (count metrics) :limit actual-limit :values metrics}
          nil))
      nil))
  
  (remove-history [this metric-ns metric-type metric-id age]
    (dosync
      (when-let [history (get-in @store [metric-ns metric-type metric-id :history])]
        (let [now (System/currentTimeMillis)
              new-history (into (sorted-map-by >) (for [metric history :while (<= (- now ((val metric) :timestamp)) age)] metric))]
          (alter store assoc-in [metric-ns metric-type metric-id :history] new-history))))
    nil)
  
  (remove-history [this metric-ns metric-type age]
    (doseq [metrics-by-id (get-in @store [metric-ns metric-type])] (remove-history this metric-ns metric-type (key metrics-by-id) age))
    nil)
  
  (merge-history [this metric-ns metric-type tags age limit] {:message "Unsupported operation over memory store."})
  
  (aggregate-history [this metric-ns metric-type metric-id from to options] {:message "Unsupported operation over memory store."})
  
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
            "CREATE CACHED TABLE metrics (ns LONGVARCHAR, type LONGVARCHAR, id LONGVARCHAR, timestamp BIGINT, metric LONGVARCHAR, primary_value DOUBLE, PRIMARY KEY (ns,type,id,timestamp))")))
      (catch Exception ex))
    (try 
      (sql/with-connection connection-factory
        (sql/transaction 
          (sql/do-prepared 
            "CREATE INDEX timestamp_value ON metrics (timestamp)")))
      (catch Exception ex))
    (try 
      (sql/with-connection connection-factory
        (sql/transaction 
          (sql/do-prepared 
            "CREATE INDEX primary_value ON metrics (primary_value)")))
      (catch Exception ex))
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared "SET DATABASE TRANSACTION CONTROL MVLOCKS"))
      (sql/transaction 
        (sql/do-prepared "SET DATABASE DEFAULT RESULT MEMORY ROWS 100000"))
      (sql/transaction 
        (sql/with-query-results 
          all-metrics
          ["SELECT ns, type, id, MAX(timestamp) AS timestamp FROM metrics GROUP BY ns, type, id"] 
          (doseq [metric all-metrics] 
            (let [latest-metric-value 
                  (sql/with-query-results 
                    latest-metric-values
                    ["SELECT metric FROM metrics WHERE ns=? AND type=? AND id=? AND timestamp=?" (metric :ns) (metric :type) (metric :id) (metric :timestamp)] 
                    (json/parse-string ((first latest-metric-values) :metric) true (fn [_] #{})))]
              (dosync (alter memory assoc-in [(metric :ns) (metric :type) (metric :id)] latest-metric-value))))))))
  
  (set-metric [this metric-ns metric-type metric-id metric primary]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/insert-record 
                         "metrics"
                         {"ns" metric-ns "type" metric-type "id" metric-id "timestamp" (metric :timestamp) "metric" (json/generate-string metric) "primary_value" primary})))
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
  
  (read-metric [this metric-ns metric-type metric-id]
    (get-in @memory [metric-ns metric-type metric-id]))
  
  (list-metrics [this metric-ns metric-type]
    (sql/with-connection connection-factory
      (sql/transaction (sql/with-query-results 
                         r 
                         ["SELECT id FROM metrics WHERE ns=? AND type=? GROUP BY id ORDER BY id" metric-ns metric-type]
                         (if (seq r)
                           (into [] (map #(get %1 :id) r))
                           nil)))))
  
  (read-history [this metric-ns metric-type metric-id tags age limit]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/with-query-results 
                         r 
                         [(str "SELECT metric FROM metrics WHERE ns=? AND type=? AND id=? AND timestamp>=? ORDER BY timestamp DESC LIMIT " (or limit default-limit) " USING INDEX") 
                          metric-ns metric-type metric-id (- (System/currentTimeMillis) (or age default-age))]
                         (if (seq r) 
                           (let [metrics (into [] (for [metric (map row-to-json r) :when (cset/subset? tags (metric :tags))] 
                                                    metric))]
                             {:size (count metrics) :limit (or limit default-limit) :values metrics})
                           nil)))))
  
  (remove-history [this metric-ns metric-type metric-id age]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/delete-rows 
                         "metrics" 
                         ["ns=? AND type=? AND id=? AND timestamp<=?" metric-ns metric-type metric-id (- (System/currentTimeMillis) age)])))
    nil)
  
  (remove-history [this metric-ns metric-type age]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/delete-rows 
                         "metrics" 
                         ["ns=? AND type=? AND timestamp<=?" metric-ns metric-type (- (System/currentTimeMillis) age)])))
    nil)
  
  (merge-history [this metric-ns metric-type tags age limit]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/with-query-results 
                         r 
                         [(str "SELECT metric FROM metrics WHERE ns=? AND type=? AND timestamp>=? ORDER BY timestamp DESC LIMIT " (or limit default-limit) " USING INDEX")
                          metric-ns metric-type (- (System/currentTimeMillis) (or age default-age))]
                         (if (seq r)
                           (let [metrics (into [] (for [metric (map row-to-json r) :when (cset/subset? tags (metric :tags))] 
                                                    metric))]
                             {:size (count metrics) :limit (or limit default-limit) :values metrics})
                           nil)))))
  
  (aggregate-history [this metric-ns metric-type metric-id from to options]
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared "SET DATABASE DEFAULT ISOLATION LEVEL SERIALIZABLE")
        (let [total (sql/with-query-results r 
                      ["SELECT COUNT(*) AS total FROM metrics WHERE ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<=?" metric-ns metric-type metric-id (or from 0) (or to Long/MAX_VALUE)]
                      ((first r) :total))]
          (if (> total 0)
            (let [percentiles-values (for [rank (percentiles total (options :percentiles))]
                                       (sql/with-query-results r 
                                         [(str "SELECT metric FROM metrics WHERE ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<=? ORDER BY primary_value ASC LIMIT 1 OFFSET " (dec (second rank)) " USING INDEX") metric-ns metric-type metric-id (or from 0) (or to Long/MAX_VALUE)]
                                         [(keyword (str (first rank) "th")) (json/parse-string ((first r) :metric) true (fn [_] #{}))]))]
              {:cardinality total :percentiles (into {} percentiles-values)})
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
               (.setJdbcUrl (str "jdbc:hsqldb:file:" path ";shutdown=true;hsqldb.log_data=false;hsqldb.cache_file_scale=128;hsqldb.cache_rows=1000"))
               (.setUser "SA")
               (.setPassword "")) 
        store (DiskStore. {:datasource pool} (ref {}))]
    (init store)
    store))