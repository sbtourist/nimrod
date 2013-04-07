(ns nimrod.core.store
 (:require
   [cheshire.core :as json]
   [clojure.java.jdbc :as sql]
   [clojure.set :as cset]
   [clojure.string :as string]
   [clojure.tools.logging :as log])
 (:use
   [nimrod.core.math]
   [nimrod.core.util]
   [nimrod.internal.stats])
 (:import 
  [java.util.concurrent LinkedBlockingQueue]
  [org.hsqldb.jdbc JDBCPool])
 (:refer-clojure :exclude (resultset-seq)))

(defonce default-batch-op-limit 100)
(defonce default-batch-queue-limit 1000)
(defonce default-defrag-op-limit 1000000)
(defonce default-cache-entries 1000)
(defonce default-cache-results 1000000)
(defonce default-sampling-factor 10)
(defonce default-sampling-frequency 0)

(defonce default-age (minutes 1))

(defonce batch-error (atom nil))
(defonce defrag-counter (atom nil))

(defn- batcher [batch-queue batch-limit defrag-limit connection-factory] 
  (proxy [Runnable] []
    (run []
      (reset! defrag-counter 0)
      (try
        (loop [batch (.take batch-queue)]
         (sql/with-connection connection-factory
          (sql/transaction 
            (loop [limit batch-limit current batch]
              (when current
                (current)
                (swap! defrag-counter inc)
                (when (> limit 0) (recur (dec limit) (.poll batch-queue)))))
            (sql/do-prepared "COMMIT WORK")
            (when (>= @defrag-counter defrag-limit)
              (do 
                (sql/do-prepared "CHECKPOINT DEFRAG")
                (reset! defrag-counter 0)))))
         (recur (.take batch-queue)))
        (catch Exception ex 
          (log/error ex (.getMessage ex))
          (reset! batch-error ex))))))

(defn- from-json-map [m]
  (json/generate-smile m))

(defn- to-json-map [v]
  (json/parse-smile v true (fn [field] (if (= "ewma" field) [] #{}))))

(defn- do-sampling? [samples sampling-frequency]
  (and (not (zero? sampling-frequency)) (= samples sampling-frequency)))

(defprotocol Store
  (init [this])
  
  (set-metric [this metric-ns metric-type metric-id metric aggregation])
  (remove-metric [this metric-ns metric-type metric-id])
  (read-metric [this metric-ns metric-type metric-id])
  
  (list-metrics [this metric-ns metric-type])
  
  (read-history [this metric-ns metric-type metric-id tags age from to])
  (remove-history [this metric-ns metric-type metric-id tags age from to])
  (aggregate-history [this metric-ns metric-type metric-id tags age from to aggregators])
  
  (list-types [this metric-ns])

  (stats [this]))

(deftype DiskStore [batch-queue connection-factory memory options sampling]
  
  Store
  
  (init [this]
    (dosync (ref-set memory {}))
    (try 
      (sql/with-connection connection-factory
        (sql/transaction 
          (sql/do-prepared 
            "CREATE CACHED TABLE metrics (ns LONGVARCHAR, type LONGVARCHAR, id LONGVARCHAR, timestamp BIGINT, seq BIGINT, metric LONGVARBINARY, aggregation DOUBLE, PRIMARY KEY (ns,type,id))")
          (sql/do-prepared 
            "CREATE CACHED TABLE history (ns LONGVARCHAR, type LONGVARCHAR, id LONGVARCHAR, timestamp BIGINT, seq BIGINT, metric LONGVARBINARY, aggregation DOUBLE, PRIMARY KEY (ns,type,id,timestamp,seq))")
          (sql/do-prepared 
            "CREATE INDEX history_seq_idx ON history (ns,type,id,seq)")))
      (catch Exception ex))
    (try 
      (sql/with-connection connection-factory
        (sql/transaction 
          (sql/do-prepared 
            "CREATE FUNCTION check_tags(metric LONGVARBINARY, tags LONGVARCHAR) RETURNS BOOLEAN NO SQL DETERMINISTIC LANGUAGE JAVA EXTERNAL NAME 'CLASSPATH:nimrod.java.TagsPredicate.contains'")))
      (catch Exception ex))
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared "SET DATABASE TRANSACTION CONTROL MVLOCKS"))
      (sql/transaction 
        (sql/do-prepared (str "SET DATABASE DEFAULT RESULT MEMORY ROWS " (or (options "cache.results") default-cache-results))))
      (sql/transaction
        (sql/do-prepared "SET TABLE history CLUSTERED ON (ns,type,id,timestamp)"))
      (sql/transaction
        (sql/with-query-results 
          all-metrics
          ["SELECT * FROM metrics"] 
          (doseq [metric all-metrics] 
            (dosync 
              (alter memory assoc-in [(metric :ns) (metric :type) (metric :id) :seq] (metric :seq))
              (alter memory assoc-in [(metric :ns) (metric :type) (metric :id) :metric] (to-json-map (metric :metric)))
              (alter memory assoc-in [(metric :ns) (metric :type) (metric :id) :samples] 0))))))
    (doto 
      (Thread. 
        (batcher 
          batch-queue 
          (or (options "batch.op-limit") default-batch-op-limit) 
          (or (options "defrag.op-limit") default-defrag-op-limit) 
          connection-factory))
      (.setDaemon true) 
      (.setName "DiskStore Batch Thread")
      (.start)))

(set-metric [this metric-ns metric-type metric-id metric aggregation]
  (when @batch-error (throw @batch-error))
  (.put batch-queue 
    (fn []
      (let 
        [now (clock)
        current-seq-value (get-in @memory [metric-ns metric-type metric-id :seq] 0)
        current-samples (get-in @memory [metric-ns metric-type metric-id :samples] 0)
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
          ; Update store stats:
          (update-rate-stats [:operations-per-second] (clock) (seconds 1))
          ; Increment sequence prior to actually using it to avoid duplicated entries:
          (dosync
            (alter memory assoc-in [metric-ns metric-type metric-id :seq] new-seq-value))
          ; Insert metric/history:
          (sql/with-query-results 
            r
            ["SELECT * FROM metrics WHERE ns=? AND type=? AND id=?" metric-ns metric-type metric-id]
            (if (seq r)
              (sql/update-values 
                "metrics"
                ["ns=? AND type=? AND id=?" metric-ns metric-type metric-id]
                {"timestamp" new-metric-timestamp "seq" new-seq-value "metric" new-json-metric "aggregation" new-aggregation-value})
              (sql/insert-records
                "metrics"
                {"ns" metric-ns "type" metric-type "id" metric-id "timestamp" new-metric-timestamp "seq" new-seq-value "metric" new-json-metric "aggregation" new-aggregation-value})))
          (sql/insert-records
            "history"
            {"ns" metric-ns "type" metric-type "id" metric-id "timestamp" new-metric-timestamp "seq" new-seq-value "metric" new-json-metric "aggregation" new-aggregation-value})
          (dosync
            (alter memory assoc-in [metric-ns metric-type metric-id :metric] metric))
          ; Optionally sample:
          (if (do-sampling? new-samples sampling-frequency)
            (do 
              (loop [record (- new-seq-value new-samples) samples new-samples to-delete (- new-samples (/ new-samples sampling-factor))]
                (if (<= (int (* samples (rand))) to-delete)
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
    (update-rate-stats [:operations-per-second] (clock) (seconds 1))
    (if (get-in @memory [metric-ns metric-type metric-id])
      (do
        (sql/with-connection connection-factory 
          (sql/transaction 
            (sql/delete-rows 
              "metrics" 
              ["ns=? AND type=? AND id=?" metric-ns metric-type metric-id])))
        (dosync
          (when-let [metrics (get-in @memory [metric-ns metric-type])]
            (alter memory assoc-in [metric-ns metric-type] (dissoc metrics metric-id))))
        true) 
      false))

  (read-metric [this metric-ns metric-type metric-id]
    (update-rate-stats [:operations-per-second] (clock) (seconds 1))
    (get-in @memory [metric-ns metric-type metric-id :metric]))

  (list-metrics [this metric-ns metric-type]
    (update-rate-stats [:operations-per-second] (clock) (seconds 1))
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/with-query-results 
         r 
         ["SELECT id FROM metrics WHERE ns=? AND type=? GROUP BY id ORDER BY id" metric-ns metric-type]
         (when (seq r)
           (into [] (map #(get %1 :id) r)))))))

  (read-history [this metric-ns metric-type metric-id tags age from to]
    (update-rate-stats [:operations-per-second] (clock) (seconds 1))
    (let [now (clock)
      actual-from (if (nil? from) (- now (or age default-age)) from)
      actual-to (or to now)]
      (sql/with-connection connection-factory 
        (sql/transaction 
          (sql/with-query-results 
            r 
            ["SELECT metric FROM history WHERE ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<=? AND check_tags(metric, ?) ORDER BY timestamp DESC" 
            metric-ns metric-type metric-id actual-from actual-to (string/join "," tags)]
            (when (seq r) 
              (let [metrics (into [] (map #(to-json-map (%1 :metric)) r))]
                {:time 
                 {:from (date-to-string actual-from) :to (date-to-string actual-to)} 
                 :count (count metrics) 
                 :values metrics})))))))

  (remove-history [this metric-ns metric-type metric-id tags age from to]
    (update-rate-stats [:operations-per-second] (clock) (seconds 1))
    (if (get-in @memory [metric-ns metric-type metric-id])
      (let 
        [now (clock)
        actual-from (or from 0) 
        actual-to (if (nil? to) (- now (or age default-age)) to)]
        (sql/with-connection connection-factory 
          (let 
            [timestamps 
            (sql/transaction 
             (sql/with-query-results r
               ["SELECT MIN(timestamp) AS mints, MAX(timestamp) AS maxts FROM history WHERE ns=? AND type=? AND id=?" metric-ns metric-type metric-id]
               (first r)))
            min-timestamp (max (timestamps :mints) actual-from)
            max-timestamp (min (timestamps :maxts) actual-to)]
            (loop [upbound (min (+ min-timestamp (days 1)) max-timestamp)]
              (sql/transaction 
                (sql/delete-rows "history" 
                  ["ns=? AND type=? AND id=? AND timestamp>=? AND timestamp<? AND check_tags(metric, ?)"
                  metric-ns metric-type metric-id min-timestamp upbound (string/join "," tags)]))
              (when (< upbound max-timestamp)
                (recur (min (+ upbound (days 1)) max-timestamp))))))
        true) 
      false))

  (aggregate-history [this metric-ns metric-type metric-id tags age from to aggregators]
    (update-rate-stats [:operations-per-second] (clock) (seconds 1))
    (sql/with-connection connection-factory
      (sql/transaction 
        (sql/do-prepared "SET DATABASE DEFAULT ISOLATION LEVEL SERIALIZABLE")
        (let 
          [now (clock)
          actual-from (if (nil? from) (- now (or age default-age)) from)
          actual-to (or to now)
          query (sql/prepare-statement 
            (sql/connection) 
            (str "SELECT metric, aggregation FROM history WHERE ns='" metric-ns "' AND type='" metric-type "' AND id='" metric-id "' AND timestamp>=" actual-from " AND timestamp<=" actual-to " AND check_tags(metric,'" (string/join "," tags) "') ORDER BY aggregation ASC") 
            :concurrency :read-only :result-type :scroll-insensitive :fetch-size 1)
          rs (.executeQuery query)]
          (when (.first rs)
            (.beforeFirst rs)
            (let [
              count-mean-variance-array (count-mean-variance #(when (.next rs) (.getDouble rs 2)))
              total (count-mean-variance-array 0)
              mean (count-mean-variance-array 1)
              variance (count-mean-variance-array 2)
              med (median total #(when (.absolute rs %1) (.getDouble rs 2)))
              perc (percentiles total (aggregators :percentiles) #(when (.absolute rs %1) (to-json-map (.getBytes rs 1))))]
            {:time 
             {:from (date-to-string actual-from) :to (date-to-string actual-to)}
             :count total
             :mean mean
             :variance variance
             :median med
             :percentiles perc
             }))))))

  (list-types [this metric-ns]
    (update-rate-stats [:operations-per-second] (clock) (seconds 1))
    (sql/with-connection connection-factory
      (sql/transaction
        (sql/with-query-results 
          all-types
          ["SELECT type FROM metrics WHERE ns=? GROUP BY type ORDER BY type" metric-ns]
          (when (seq all-types) (into [] (for [type all-types] (type :type))))))))

  (stats [this]
    (show-stats [[:operations-per-second]] (clock) (seconds 1))))

(defn new-disk-store
  ([path options sampling]
    (when (seq options) (log/info (str "Starting DiskStore located in " path " with options: " options)))
    (when (seq sampling) (log/info (str "Sampling with: " sampling)))
    (let 
      [batch-queue-limit (or (options "batch.queue-limit") default-batch-queue-limit)
      cache-entries (or (options "cache.entries") default-cache-entries)
      pool (doto (JDBCPool.)
       (.setUrl (str 
        "jdbc:hsqldb:file:" path ";"
        "autocommit=false;shutdown=true;hsqldb.applog=2;hsqldb.log_size=50;hsqldb.cache_file_scale=128;hsqldb.defrag_limit=0;"
        "hsqldb.cache_rows=" cache-entries ";" 
        "hsqldb.cache_size=" cache-entries))
       (.setUser "SA")
       (.setPassword ""))
      disk-store (DiskStore. (LinkedBlockingQueue. batch-queue-limit) {:datasource pool} (ref nil) options sampling)]
      (.addShutdownHook (Runtime/getRuntime) (proxy [Thread] [] (run [] (.close pool 1))))
      (init disk-store)
      disk-store))
  ([path options] (new-disk-store path options {}))
  ([path] (new-disk-store path {} {})))

(defonce store (atom nil))

(defn start-store [s]
  (reset! store s))
