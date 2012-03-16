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
  (aggregate-history [this metric-ns metric-type metric-id age from to options])
  (list-types [this metric-ns]))


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
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared "SET DATABASE TRANSACTION CONTROL MVCC"))
      (sql/transaction 
        (sql/do-prepared "SET DATABASE DEFAULT RESULT MEMORY ROWS 100000000"))))
  
  (set-metric [this metric-ns metric-type metric-id metric primary]
    (sql/with-connection connection-factory 
      (sql/transaction (sql/update-or-insert-values 
                         "metrics"
                         ["ns=? AND type=? AND id=? AND timestamp=?" metric-ns metric-type metric-id (metric :timestamp)]
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
                           (let [metrics (into [] (for [metric (map row-to-json r) :when (cset/subset? tags (metric :tags))] metric))]
                             {:size (count metrics) :limit (or limit default-limit) :values metrics})
                           nil)))))
  
  (aggregate-history [this metric-ns metric-type metric-id age from to options]
    (sql/with-connection connection-factory
      (sql/transaction 
        (let [values (sql/with-query-results r 
                       ["SELECT timestamp, primary_value FROM metrics WHERE ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<=? ORDER BY primary_value ASC" 
                        metric-ns metric-type metric-id 
                        (max (- (System/currentTimeMillis) (or age default-age)) (or from 0)) (or to Long/MAX_VALUE)]
                       (let [accumulator (reduce #(conj! %1 %2) (transient []) r)] (persistent! accumulator)))]
          (when (seq values)
            {:cardinality 
             (count values) 
             :percentiles
             (into {} 
               (for [p (percentiles values (options :percentiles))]
                 (sql/with-query-results 
                   r 
                   ["SELECT metric FROM metrics WHERE ns=? AND type=? AND id=? AND timestamp=?" metric-ns metric-type metric-id ((val p) :timestamp)]
                   [(key p) (row-to-json (first r))])))})))))
  
  (list-types [this metric-ns]
    (sql/with-connection connection-factory
      (sql/transaction
        (sql/with-query-results 
          all-types
          ["SELECT type FROM metrics WHERE ns=? GROUP BY type ORDER BY type" metric-ns]
          (into [] (for [type all-types] (type :type))))))))  


(defn new-disk-store [path] 
  (let [pool (doto (ComboPooledDataSource.)
               (.setDriverClass "org.hsqldb.jdbc.JDBCDriver") 
               (.setJdbcUrl (str "jdbc:hsqldb:file:" path ";shutdown=true;hsqldb.log_size=10;hsqldb.cache_file_scale=128;hsqldb.cache_rows=1000;hsqldb.cache_size=10000"))
               (.setUser "SA")
               (.setPassword "")
               (.setMinPoolSize 1)
               (.setMaxPoolSize 10)
               (.setInitialPoolSize 0)
               (.setAcquireIncrement 1)
               (.setNumHelperThreads 5))
        store (DiskStore. {:datasource pool} (ref {}))]
    (.addShutdownHook (Runtime/getRuntime) (proxy [Thread] [] (run [] (.close pool))))
    (init store)
    store))