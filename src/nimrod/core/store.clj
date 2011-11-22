(ns nimrod.core.store
 (:use
   [nimrod.core.util]
   [clojure.set :as cset]))

(defprotocol Store
  (set-metric [this metric-ns metric-type metric-id metric])
  (remove-metric [this metric-ns metric-type metric-id])
  (remove-metrics [this metric-ns metric-type metric-id age])
  (read-metric [this metric-ns metric-type metric-id])
  (read-metrics [this metric-ns metric-type metric-id age tags])
  (list-metrics [this metric-ns metric-type])
  )

(deftype MemoryStore [store]
  Store
  (set-metric [this metric-ns metric-type metric-id metric]
    (dosync
      (if-let [values (get-in @store [metric-ns metric-type metric-id])]
        (do
          (alter store assoc-in [metric-ns metric-type metric-id :current] metric)
          (alter store assoc-in [metric-ns metric-type metric-id :history (metric :timestamp)] (unrationalize metric)))
        (let [values {:current metric :history (sorted-map-by > (metric :timestamp) (unrationalize metric))}]
          (alter store assoc-in [metric-ns metric-type metric-id] values))))
    nil)
  (remove-metric [this metric-ns metric-type metric-id]
    (dosync
      (when-let [metrics (get-in @store [metric-ns metric-type])]
        (alter store assoc-in [metric-ns metric-type] (dissoc metrics metric-id))))
    nil)
  (remove-metrics [this metric-ns metric-type metric-id age]
    (dosync
      (when-let [history (get-in @store [metric-ns metric-type metric-id :history])]
        (let [now (System/currentTimeMillis)]
          (alter store assoc-in [metric-ns metric-type metric-id :history] 
            (into (sorted-map-by >) (for [metric history :while (<= (- now ((val metric) :timestamp)) age)] metric))))))
    nil)
  (read-metric [this metric-ns metric-type metric-id]
    (dosync
      (when-let [current (get-in @store [metric-ns metric-type metric-id :current])]
        current)))
  (read-metrics [this metric-ns metric-type metric-id age tags]
    (dosync
      (if-let [history (get-in @store [metric-ns metric-type metric-id :history])]
        (let [now (System/currentTimeMillis)]
          (into [] (for [metric (vals history) :while (<= (- now (metric :timestamp)) age) :when (cset/subset? tags (metric :tags))] metric)))
        [])))
  (list-metrics [this metric-ns metric-type]
    (dosync
      (if-let [metrics (get-in @store [metric-ns metric-type])]
        (into [] (sort (keys metrics)))
        []))))

(defn new-memory-store [] (MemoryStore. (ref {})))