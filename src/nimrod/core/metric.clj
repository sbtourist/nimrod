(ns nimrod.core.metric
 (:require
   [clojure.tools.logging :as log])
 (:use 
   [nimrod.core.math]
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
  (aggregation-value-of [this metric] 0)
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) alert new-value]
      (if-let [current current-value]
        (let [samples (inc (current :samples))]
          (conj current 
            {:timestamp new-time
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
        (let 
          [samples (inc (current :samples))]
          (conj current 
            {:timestamp new-time
             :gauge gauge
             :samples samples
             :tags tags}))
        {:id id
         :timestamp new-time
         :gauge gauge
         :samples 1
         :tags tags}))))

(deftype Counter []
  MetricType
  (name-of [this] "nimrod.core.metric.Counter")
  (aggregation-value-of [this metric] (metric :counter))
  (compute [this id timestamp current-value new-value tags]
    (let [new-time (Long/parseLong timestamp) increment (Long/parseLong new-value)]
      (if-let [current current-value]
        (let 
          [samples (inc (current :samples))
          previous-counter (current :counter)]
          (conj current 
            {:timestamp new-time
             :counter (+ previous-counter increment)
             :samples samples
             :latest-increment increment
             :tags tags}))
        {:id id
         :timestamp new-time
         :counter increment
         :samples 1
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
            (let 
              [start (current :start)
              samples (inc (current :samples))
              elapsed-time (- timer start)]
              (conj current 
                {:timestamp new-time
                 :end timer
                 :elapsed-time elapsed-time
                 :samples samples
                 :tags tags}))
            :else (throw (IllegalStateException. (str "Bad timer action: " action))))
          (if (= "start" action)
            {:id id :timestamp new-time :start timer :end 0 :elapsed-time 0 :samples 0 :tags tags}
            (throw (IllegalStateException. (str "Bad timer action, first time must always be 'start', not: " action))))))))

(defonce alert (Alert.))
(defonce gauge (Gauge.))
(defonce counter (Counter.))
(defonce timer (Timer.))

(defn compute-metric [type metric-ns metric-id timestamp value tags]
  (let 
    [now (clock)
    current-metric (read-metric @metrics-store metric-ns (name-of type) metric-id)
    new-metric (assoc (compute type metric-id timestamp current-metric value tags) :systemtime (date-to-string now))]
    (try 
      (set-metric @metrics-store metric-ns (name-of type) metric-id new-metric (aggregation-value-of type new-metric)) 
      (catch Exception ex (log/error (.getMessage ex) ex)))))
