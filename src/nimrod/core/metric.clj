(ns nimrod.core.metric
 (:use [nimrod.core.stat]
   [nimrod.core.store]
   [nimrod.core.util]
   [clojure.tools.logging :as log]))

(defprotocol MetricType
  (name-of [this])
  (compute [this id timestamp current-value new-value tags]))

(deftype Alert []
  MetricType
  (name-of [this] "nimrod.core.metric.Alert")
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) alert new-value]
      (if-let [current current-value]
        (let [previous-time (current :timestamp)
              previous-interval-average (current :interval-average)
              previous-interval-variance (current :interval-variance)
              interval (- new-time previous-time)
              samples (inc (current :samples))
              interval-average (average (dec samples) previous-interval-average interval)
              interval-variance (variance (dec samples) previous-interval-variance previous-interval-average interval-average interval)]
          (conj current {:timestamp new-time
                         :alert alert
                         :samples samples
                         :interval-average interval-average
                         :interval-variance interval-variance
                         :tags tags}))
        {:id id 
         :timestamp new-time
         :samples 1
         :interval-average 0
         :interval-variance 0
         :alert alert
         :tags tags}))))

(deftype Gauge []
  MetricType
  (name-of [this] "nimrod.core.metric.Gauge")
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) gauge (Long/parseLong new-value)]
      (if-let [current current-value]
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
                         }))
        {:id id
         :timestamp new-time
         :gauge gauge
         :samples 1
         :interval-average 0
         :interval-variance 0
         :gauge-average gauge
         :gauge-variance 0
         :tags tags}))))

(deftype Counter []
  MetricType
  (name-of [this] "nimrod.core.metric.Counter")
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) increment (Long/parseLong new-value)]
      (if-let [current current-value]
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
                         }))
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
         :tags tags}))))

(deftype Timer []
  MetricType
  (name-of [this] "nimrod.core.metric.Timer")
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) timer new-time action new-value]
      (if-let [current current-value]
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
                           :tags tags}))
          :else (throw (IllegalStateException. (str "Bad timer action: " action))))
        (if (= "start" action)
          {:id id :timestamp new-time :start timer :end 0 :elapsed-time 0 :elapsed-time-average 0 :elapsed-time-variance 0 :samples 0 :tags tags}
          (throw (IllegalStateException. (str "Bad timer action, first time must always be 'start', not: " action))))))))

(defn new-alert [] (Alert.))

(defn new-gauge [] (Gauge.))

(defn new-counter [] (Counter.))

(defn new-timer [] (Timer.))

(defn new-metric-agent [store] (new-agent store))

(defonce alert (new-alert))
(defonce gauge (new-gauge))
(defonce counter (new-counter))
(defonce timer (new-timer))

(defonce metric-agent (new-metric-agent nil))

(defn setup-metric-store [store]
  (send metric-agent (fn [_] store)))

(defn compute-metric [type metric-ns metric-id timestamp value tags]
  (send metric-agent (fn [store] 
                       (let [current-metric (read-metric store metric-ns (name-of type) metric-id)
                             new-metric (assoc (compute type metric-id timestamp current-metric value tags) :systemtime (date-to-string (System/currentTimeMillis)))]
                         (try 
                           (set-metric store metric-ns (name-of type) metric-id new-metric) 
                           (catch Exception ex (log/error (.getMessage ex) ex)))
                         store))))

(defn aggregate-metric [metrics value percs]
  {:metric value
   :cardinality (count metrics)
   :percentiles (percentiles (into [] (sort (for [metric metrics] (metric (keyword value))))) percs)})