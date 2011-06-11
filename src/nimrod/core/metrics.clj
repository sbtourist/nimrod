(ns nimrod.core.metrics
 (:use
   [clojure.set :as cset]
   [nimrod.core.stat]
   [nimrod.core.util])
 )

; ---

(defn- init-history 
  ([limit]
    {:limit limit :size 0 :values []})
  ([limit value]
    {:limit limit :size 1 :values [value]})
  )

(defn- update-history [history value]
  (let [limit (history :limit) values (history :values) size (count values)]
    (if (= size limit)
      (let [new-values (conj (apply vector (rest values)) value)]
        (assoc history :values new-values :size (count new-values))
        )
      (let [new-values (conj values value)]
        (assoc history :values new-values :size (count new-values))
        )
      )
    )
  )

; ---

(defn- compute-status [current id timestamp value tags]
  (let [new-time (Long/parseLong timestamp) status value]
    (if-let [current current]
      (conj current {:timestamp new-time :status status :tags tags})
      {:id id :timestamp new-time :status status :tags tags}
      )
    )
  )

; ---

(defn- compute-gauge [current id timestamp value tags]
  (let [new-time (Long/parseLong timestamp) gauge (Long/parseLong value)]
    (if-let [current current]
      (let [previous-time (current :timestamp)
            previous-interval-average (current :interval-average)
            previous-interval-variance (current :interval-variance)
            previous-gauge-average (current :gauge-average)
            previous-gauge-variance (current :gauge-variance)
            interval (- new-time previous-time)
            samples (inc (current :samples))
            interval-average (average (dec samples) previous-interval-average interval)
            interval-variance (variance (dec samples) previous-interval-variance previous-interval-average interval-average interval)
            gauge-average (average samples previous-gauge-average gauge)
            gauge-variance (variance samples previous-gauge-variance previous-gauge-average gauge-average gauge)]
        (conj current {:timestamp new-time
                       :gauge gauge
                       :samples samples
                       :interval-average interval-average
                       :interval-variance interval-variance
                       :gauge-average gauge-average
                       :gauge-variance gauge-variance
                       :tags tags
                       })
        )
      {:id id
       :timestamp new-time
       :gauge gauge
       :samples 1
       :interval-average 0
       :interval-variance 0
       :gauge-average gauge
       :gauge-variance 0
       :tags tags}
      )
    )
  )

; ---

(defn- compute-counter [current id timestamp value tags]
  (let [new-time (Long/parseLong timestamp) increment (Long/parseLong value)]
    (if-let [current current]
      (let [previous-time (current :timestamp)
            previous-counter (current :counter)
            previous-interval-average (current :interval-average)
            previous-interval-variance (current :interval-variance)
            previous-increment-average (current :increment-average)
            previous-increment-variance (current :increment-variance)
            interval (- new-time previous-time)
            samples (inc (current :samples))
            interval-average (average (dec samples) previous-interval-average interval)
            interval-variance (variance (dec samples) previous-interval-variance previous-interval-average interval-average interval)
            increment-average (average samples previous-increment-average increment)
            increment-variance (variance samples previous-increment-variance previous-increment-average increment-average increment)]
        (conj current {:timestamp new-time
                       :counter (+ previous-counter increment)
                       :samples samples
                       :interval-average interval-average
                       :interval-variance interval-variance
                       :latest-interval interval
                       :increment-average increment-average
                       :increment-variance increment-variance
                       :latest-increment increment
                       :tags tags
                       })
        )
      {:id id
       :timestamp new-time
       :counter increment
       :samples 1
       :interval-average 0
       :interval-variance 0
       :latest-interval 0
       :increment-average increment
       :increment-variance 0
       :latest-increment increment
       :tags tags}
      )
    )
  )

; ---

(defn- compute-timer [current id timestamp value tags]
  (let [new-time (Long/parseLong timestamp) timer new-time action value]
    (if-let [current current]
      (cond
        (= "start" action)
        (conj current {:timestamp new-time :start timer :end 0 :elapsed-time 0 :tags tags})
        (= "stop" action)
        (let [previous-elapsed-time-average (current :elapsed-time-average)
              previous-elapsed-time-variance (current :elapsed-time-variance)
              start (current :start)
              samples (inc (current :samples))
              elapsed-time (- timer start)
              elapsed-time-average (average samples previous-elapsed-time-average elapsed-time)
              elapsed-time-variance (variance samples previous-elapsed-time-variance previous-elapsed-time-average elapsed-time-average elapsed-time)]
          (conj current {:timestamp new-time
                         :end timer
                         :elapsed-time elapsed-time
                         :elapsed-time-average elapsed-time-average
                         :elapsed-time-variance elapsed-time-variance
                         :samples samples
                         :tags tags})
          )
        :else (throw (IllegalStateException. (str "Bad timer action: " action)))
        )
      (if (= "start" action)
        {:id id :timestamp new-time :start timer :end 0 :elapsed-time 0 :elapsed-time-average 0 :elapsed-time-variance 0 :samples 0 :tags tags}
        (throw (IllegalStateException. (str "Bad timer action, first time must always be 'start', not: " action)))
        )
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
  (set-metric [this metric-ns metric-id timestamp value tags])
  (read-metric [this metric-ns metric-id])
  (list-metrics [this metric-ns])
  (flush-metrics [this metric-ns])
  (read-history [this metric-ns metric-id tags])
  (reset-history [this metric-ns metric-id limit])
  )

(deftype Metric [metric-type compute-fn]
  MetricProtocol
  (set-metric [this metric-ns metric-id timestamp value tags]
    (let [metric (get-or-create-metric metric-type metric-ns metric-id)]
      (send metric #(let [state (or %1 {:history (init-history 100) :computation nil :value nil})
                          computed (compute-fn (state :computation) metric-id timestamp value tags)
                          displayed (display computed)]
                      (conj state {:history (update-history (state :history) displayed) :computation computed :value displayed})
                      )
        )
      )
    )
  (read-metric [this metric-ns metric-id]
    (if-let [metric ((get @metric-type metric-ns {}) metric-id)]
      (@metric :value)
      nil
      )
    )
  (list-metrics [this metric-ns]
    (if-let [metrics (@metric-type metric-ns)]
      (apply vector (sort (keys metrics)))
      []
      )
    )
  (flush-metrics [this metric-ns]
    (if-let [metrics (@metric-type metric-ns)]
      (apply await (vals metrics))
      []
      )
    )
  (read-history [this metric-ns metric-id tags]
    (if-let [metric ((get @metric-type metric-ns {}) metric-id)]
      (if (seq tags)
        (let [history (@metric :history) filtered-values (filter #(cset/subset? tags (%1 :tags)) (history :values))]
          (assoc history :size (count filtered-values) :values (apply vector filtered-values))
          )
        (@metric :history)
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
                       :statuses (Metric. (ref {}) compute-status)
                       :gauges (Metric. (ref {}) compute-gauge)
                       :counters (Metric. (ref {}) compute-counter)
                       :timers (Metric. (ref {}) compute-timer)
                       })