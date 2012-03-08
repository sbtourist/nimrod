(ns nimrod.core.metric
 (:use [nimrod.core.stat]
   [nimrod.core.store]
   [nimrod.core.util]
   [clojure.tools.logging :as log])
 (:import [java.util.concurrent ArrayBlockingQueue]))

(defprotocol MetricType
  (name-of [this])
  (compute [this id timestamp current-value new-value tags])
  (primary [this metric]))

(deftype Alert []
  MetricType
  (name-of [this] "nimrod.core.metric.Alert")
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) alert new-value]
      (if-let [current current-value]
        (let [samples (inc (current :samples))]
          (conj current {:timestamp new-time
                         :alert alert
                         :samples samples
                         :tags tags}))
        {:id id 
         :timestamp new-time
         :samples 1
         :alert alert
         :tags tags})))
  (primary [this metric] (metric :timestamp)))

(deftype Gauge []
  MetricType
  (name-of [this] "nimrod.core.metric.Gauge")
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) gauge (Long/parseLong new-value)]
      (if-let [current current-value]
        (let [samples (inc (current :samples))
              previous-gauge-average (current :gauge-average)
              previous-gauge-variance (current :gauge-variance)
              gauge-average (average samples previous-gauge-average gauge)
              gauge-variance (variance samples previous-gauge-variance previous-gauge-average gauge-average gauge)]
          (conj current {:timestamp new-time
                         :gauge gauge
                         :samples samples
                         :gauge-average gauge-average
                         :gauge-variance gauge-variance
                         :tags tags
                         }))
        {:id id
         :timestamp new-time
         :gauge gauge
         :samples 1
         :gauge-average gauge
         :gauge-variance 0
         :tags tags})))
  (primary [this metric] (metric :gauge)))

(deftype Counter []
  MetricType
  (name-of [this] "nimrod.core.metric.Counter")
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) increment (Long/parseLong new-value)]
      (if-let [current current-value]
        (let [samples (inc (current :samples))
              previous-counter (current :counter)
              previous-increment-average (current :increment-average)
              previous-increment-variance (current :increment-variance)
              increment-average (average samples previous-increment-average increment)
              increment-variance (variance samples previous-increment-variance previous-increment-average increment-average increment)]
          (conj current {:timestamp new-time
                         :counter (+ previous-counter increment)
                         :samples samples
                         :increment-average increment-average
                         :increment-variance increment-variance
                         :latest-increment increment
                         :tags tags
                         }))
        {:id id
         :timestamp new-time
         :counter increment
         :samples 1
         :increment-average increment
         :increment-variance 0
         :latest-increment increment
         :tags tags})))
  (primary [this metric] (metric :counter)))

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
          (throw (IllegalStateException. (str "Bad timer action, first time must always be 'start', not: " action)))))))
  (primary [this metric] (metric :elapsed-time)))

(defn new-alert [] (Alert.))

(defn new-gauge [] (Gauge.))

(defn new-counter [] (Counter.))

(defn new-timer [] (Timer.))

(defonce alert (new-alert))
(defonce gauge (new-gauge))
(defonce counter (new-counter))
(defonce timer (new-timer))

(defonce metrics-store (atom nil))

(defn setup-metric-store [store]
  (reset! metrics-store store))

(defn compute-metric [type metric-ns metric-id timestamp value tags]
  (let [current-metric (read-metric @metrics-store metric-ns (name-of type) metric-id)
        new-metric (assoc (compute type metric-id timestamp current-metric value tags) :systemtime (date-to-string (System/currentTimeMillis)))]
    (try 
      (set-metric @metrics-store metric-ns (name-of type) metric-id new-metric (primary type new-metric)) 
      (catch Exception ex (log/error (.getMessage ex) ex)))))