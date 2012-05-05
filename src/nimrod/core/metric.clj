(ns nimrod.core.metric
 (:require
   [clojure.tools.logging :as log])
 (:use 
   [nimrod.core.stat]
   [nimrod.core.store]
   [nimrod.core.util])
 (:import [java.util.concurrent ArrayBlockingQueue]))

(defprotocol MetricType
  (name-of [this])
  (aggregation-value-of [this metric])
  (compute [this id timestamp current-value new-value tags]))

(deftype Alert []
  MetricType
  (name-of [this] "nimrod.core.metric.Alert")
  (aggregation-value-of [this metric] (metric :timestamp))
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
         :tags tags}))))

(deftype Gauge []
  MetricType
  (name-of [this] "nimrod.core.metric.Gauge")
  (aggregation-value-of [this metric] (metric :gauge))
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) gauge (Long/parseLong new-value)]
      (if-let [current current-value]
        (let [samples (inc (current :samples))
              previous-gauge-mean (current :gauge-mean)
              previous-gauge-variance (current :gauge-variance)
              gauge-mean (mean samples previous-gauge-mean gauge)
              gauge-variance (variance samples previous-gauge-variance previous-gauge-mean gauge-mean gauge)]
          (conj current {:timestamp new-time
                         :gauge gauge
                         :samples samples
                         :gauge-mean gauge-mean
                         :gauge-variance gauge-variance
                         :tags tags
                         }))
        {:id id
         :timestamp new-time
         :gauge gauge
         :samples 1
         :gauge-mean gauge
         :gauge-variance 0
         :tags tags}))))

(deftype Counter []
  MetricType
  (name-of [this] "nimrod.core.metric.Counter")
  (aggregation-value-of [this metric] (metric :counter))
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) increment (Long/parseLong new-value)]
      (if-let [current current-value]
        (let [samples (inc (current :samples))
              previous-counter (current :counter)
              previous-increment-mean (current :increment-mean)
              previous-increment-variance (current :increment-variance)
              increment-mean (mean samples previous-increment-mean increment)
              increment-variance (variance samples previous-increment-variance previous-increment-mean increment-mean increment)]
          (conj current {:timestamp new-time
                         :counter (+ previous-counter increment)
                         :samples samples
                         :increment-mean increment-mean
                         :increment-variance increment-variance
                         :latest-increment increment
                         :tags tags
                         }))
        {:id id
         :timestamp new-time
         :counter increment
         :samples 1
         :increment-mean increment
         :increment-variance 0
         :latest-increment increment
         :tags tags}))))

(deftype Timer []
  MetricType
  (name-of [this] "nimrod.core.metric.Timer")
  (aggregation-value-of [this metric] (metric :elapsed-time))
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) timer new-time action new-value]
      (if-let [current current-value]
        (cond
          (= "start" action)
          (conj current {:timestamp new-time :start timer :end 0 :elapsed-time 0 :tags tags})
          (= "stop" action)
          (let [previous-elapsed-time-mean (current :elapsed-time-mean)
                previous-elapsed-time-variance (current :elapsed-time-variance)
                start (current :start)
                samples (inc (current :samples))
                elapsed-time (- timer start)
                elapsed-time-mean (mean samples previous-elapsed-time-mean elapsed-time)
                elapsed-time-variance (variance samples previous-elapsed-time-variance previous-elapsed-time-mean elapsed-time-mean elapsed-time)]
            (conj current {:timestamp new-time
                           :end timer
                           :elapsed-time elapsed-time
                           :elapsed-time-mean elapsed-time-mean
                           :elapsed-time-variance elapsed-time-variance
                           :samples samples
                           :tags tags}))
          :else (throw (IllegalStateException. (str "Bad timer action: " action))))
        (if (= "start" action)
          {:id id :timestamp new-time :start timer :end 0 :elapsed-time 0 :elapsed-time-mean 0 :elapsed-time-variance 0 :samples 0 :tags tags}
          (throw (IllegalStateException. (str "Bad timer action, first time must always be 'start', not: " action))))))))

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
      (set-metric @metrics-store metric-ns (name-of type) metric-id new-metric (aggregation-value-of type new-metric)) 
      (catch Exception ex (log/error (.getMessage ex) ex)))))