(ns nimrod.core.metrics
 (:use 
   [nimrod.core.stat]
   [nimrod.core.util])
 )

; ---

(defn- init-history 
  ([limit]
    {:limit limit :size 0 :values (sorted-map)})
  ([limit timestamp value]
    {:limit limit :size 0 :values (sorted-map timestamp value)})
  )

(defn- update-history [history timestamp value]
  (let [limit (history :limit) size (history :size) values (history :values)]
    (if (= size limit)
      (conj history {:values (assoc (dissoc values (first (keys values))) timestamp value)})
      (conj history {:size (inc size) :values (assoc values timestamp value)})
      )
    )
  )

; ---

(defn- compute-gauge [current id timestamp value]
  (let [new-timestamp (Long/parseLong timestamp) new-value value]
    (if (not (nil? current))
      (conj current {:timestamp new-timestamp :value new-value})
      {:id id :timestamp new-timestamp :value new-value}
      )
    )
  )

; ---

(defn- compute-counter [current id timestamp value]
  (let [new-timestamp (Long/parseLong timestamp) new-value (Long/parseLong value)]
    (if (not (nil? current))
      (let [previous-time (current :timestamp)
            previous-value (current :value)
            previous-interval-average (current :interval-average)
            previous-value-average (current :value-average)
            previous-interval-variance (current :interval-variance)
            previous-value-variance (current :value-variance)
            interval (- new-timestamp previous-time)
            samples (inc (current :samples))
            interval-average (average samples previous-interval-average interval)
            value-average (average samples previous-value-average new-value)
            interval-variance (variance samples previous-interval-variance previous-interval-average interval-average interval)
            value-variance (variance samples previous-value-variance previous-value-average value-average new-value)]
        (conj current {:timestamp new-timestamp
                       :value (+ previous-value new-value)
                       :samples samples
                       :interval-average interval-average
                       :value-average value-average
                       :interval-variance interval-variance
                       :value-variance value-variance
                       })
        )
      {:id id
       :timestamp new-timestamp
       :value new-value
       :samples 1
       :interval-average new-timestamp
       :interval-variance 0
       :value-average new-value
       :value-variance 0}
      )
    )
  )

; ---

(defn- compute-timer [current id timestamp value]
  (let [new-timestamp (Long/parseLong timestamp) new-value (Long/parseLong value)]
    (if (not (nil? current))
      (if (= 0 (current :end))
        (let [previous-elapsed-time-average (current :elapsed-time-average)
              previous-elapsed-time-variance (current :elapsed-time-variance)
              start (current :start)
              samples (inc (current :samples))
              elapsed-time (- new-value start)
              elapsed-time-average (average samples previous-elapsed-time-average elapsed-time)
              elapsed-time-variance (variance samples previous-elapsed-time-variance previous-elapsed-time-average elapsed-time-average elapsed-time)]
          (conj current {:timestamp new-timestamp
                         :end new-value
                         :elapsed-time elapsed-time
                         :elapsed-time-average elapsed-time-average
                         :elapsed-time-variance elapsed-time-variance
                         :samples samples})
          )
        (conj current {:timestamp new-timestamp :start new-value :end 0 :elapsed-time 0})
        )
      {:id id :timestamp new-timestamp :start new-value :end 0 :elapsed-time 0 :elapsed-time-average 0 :elapsed-time-variance 0 :samples 0}
      )
    )
  )

; ---

(defn- get-or-create-metric [metrics metric-ns metric-id]
  (dosync
    (if-let [metric ((get @metrics metric-ns {}) metric-id)]
      metric
      (let [metric (new-agent nil)]
        (alter metrics assoc-in [metric-ns metric-id] metric)
        metric
        )
      )
    )
  )

; ---

(defprotocol MetricProtocol
  (set-metric [this metric-ns metric-id timestamp value])
  (read-metric [this metric-ns metric-id])
  (list-metrics [this metric-ns])
  (flush-metrics [this metric-ns])
  (read-history [this metric-ns metric-id])
  (reset-history [this metric-ns metric-id limit])
  )

(deftype Metric [metric-type compute-fn]
  MetricProtocol
  (set-metric [this metric-ns metric-id timestamp value]
    (let [metric (get-or-create-metric metric-type metric-ns metric-id)]
      (send metric #(let [state (or %1 {:history (init-history 100) :value nil}) computed (compute-fn (state :value) metric-id timestamp value)]
                      (conj state {:history (update-history (state :history) timestamp computed) :value computed})
                      )
        )
      )
    )
  (read-metric [this metric-ns metric-id]
    (if-let [metrics-in-ns (@metric-type metric-ns)]
      (if-let [metric (metrics-in-ns metric-id)]
        (@metric :value)
        nil
        )
      nil
      )
    )
  (list-metrics [this metric-ns]
    (if-let [metrics-in-ns (@metric-type metric-ns)]
      (into [] (keys metrics-in-ns))
      []
      )
    )
  (flush-metrics [this metric-ns]
    (if-let [metrics-in-ns (@metric-type metric-ns)]
      (apply await (vals metrics-in-ns))
      []
      )
    )
  (read-history [this metric-ns metric-id]
    (if-let [metrics-in-ns (@metric-type metric-ns)]
      (if-let [metric (metrics-in-ns metric-id)]
        (@metric :history)
        nil
        )
      nil
      )
    )
  (reset-history [this metric-ns metric-id limit]
    (let [metric (get-or-create-metric metric-type metric-ns metric-id)]
      (send metric #(let [state (or %1 {:history nil :value nil})]
                      {:history (init-history limit) :value (state :value)}
                      )
        )
      )
    )
  )

; ---

(defonce metric-types {
                       :gauges (Metric. (ref {}) compute-gauge)
                       :counters (Metric. (ref {}) compute-counter)
                       :timers (Metric. (ref {}) compute-timer)
                       })