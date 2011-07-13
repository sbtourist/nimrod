(ns nimrod.core.metrics
 (:use
   [clojure.set :as cset]
   [nimrod.core.stat]
   [nimrod.core.util])
 )

; ---

(defn- compute-alert [current id timestamp value tags]
  (let [new-time (Long/parseLong timestamp) alert value]
    (if-let [current current]
      (conj current {:timestamp new-time :alert alert :tags tags})
      {:id id :timestamp new-time :alert alert :tags tags}
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

(defn- get-or-create-metric [metric-type metric-ns metric-id]
  (if-let [metric ((get @metric-type metric-ns {}) metric-id)]
    metric
    (let [metric (ref {:history (init-history 100) :computed-value nil :displayed-value nil :update-time nil})]
      (alter metric-type assoc-in [metric-ns metric-id] metric)
      metric
      )
    )
  )

(defn- get-metric [metric-type metric-ns metric-id]
  ((get @metric-type metric-ns {}) metric-id)
  )

; ---

(defprotocol MetricProtocol
  (set-metric [this metric-ns metric-id timestamp value tags])
  (read-metric [this metric-ns metric-id])
  (remove-metric [this metric-ns metric-id])
  (list-metrics [this metric-ns tags])
  (remove-metrics [this metric-ns tags])
  (expire-metrics [this metric-ns age])
  (read-history [this metric-ns metric-id tags])
  (reset-history [this metric-ns metric-id limit])
  )

(deftype MetricType [metric-type compute-fn]
  MetricProtocol
  (set-metric [this metric-ns metric-id timestamp value tags]
    (dosync
      (let [_ (ensure metric-type) metric (get-or-create-metric metric-type metric-ns metric-id)]
        (let [t (System/currentTimeMillis)
              computed (compute-fn (@metric :computed-value) metric-id timestamp value tags)
              displayed (display computed t)]
          (ref-set metric {:history (update-history (@metric :history) displayed) :computed-value computed :displayed-value displayed :update-time t})
          )
        )
      )
    )
  (read-metric [this metric-ns metric-id]
    (dosync
      (if-let [metric (get-metric metric-type metric-ns metric-id)]
        (@metric :displayed-value)
        nil
        )
      )
    )
  (remove-metric [this metric-ns metric-id]
    (dosync
      (when-let [metrics (@metric-type metric-ns)]
        (alter metric-type conj [metric-ns (dissoc metrics metric-id)])
        )
      )
    )
  (list-metrics [this metric-ns tags]
    (dosync
      (if-let [metrics (@metric-type metric-ns)]
        (if (seq tags)
          (apply vector (sort (keys (into {} (filter #(cset/subset? tags ((@(second %1) :computed-value) :tags)) metrics)))))
          (apply vector (sort (keys metrics)))
          )
        []
        )
      )
    )
  (remove-metrics [this metric-ns tags]
    (dosync
      (when-let [metrics (@metric-type metric-ns)]
        (alter metric-type conj [metric-ns (into {} (filter #(not (cset/subset? tags ((@(second %1) :computed-value) :tags))) metrics))])
        )
      )
    )
  (expire-metrics [this metric-ns age]
    (dosync
      (when-let [metrics (@metric-type metric-ns)]
        (alter metric-type conj [metric-ns (into {} (filter #(< (- (System/currentTimeMillis) (@(second %1) :update-time)) age) metrics))])
        )
      )
    )
  (read-history [this metric-ns metric-id tags]
    (dosync
      (if-let [metric (get-metric metric-type metric-ns metric-id)]
        (if (seq tags)
          (let [history (@metric :history) filtered-values (filter #(cset/subset? tags (%1 :tags)) (history :values))]
            (assoc history :size (count filtered-values) :values (apply vector filtered-values))
            )
          (@metric :history)
          )
        nil
        )
      )
    )
  (reset-history [this metric-ns metric-id limit]
    (dosync
      (let [metric (get-or-create-metric metric-type metric-ns metric-id)]
        (alter metric conj {:history (init-history limit)})
        )
      )
    )
  )

; ---

(defonce metric-types {
                       :alert (MetricType. (ref {}) compute-alert)
                       :gauge (MetricType. (ref {}) compute-gauge)
                       :counter (MetricType. (ref {}) compute-counter)
                       :timer (MetricType. (ref {}) compute-timer)
                       })