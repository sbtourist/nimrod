(ns nimrod.core.metric
 (:require
   [clojure.tools.logging :as log])
 (:use 
   [nimrod.core.math]
   [nimrod.core.store]
   [nimrod.core.util])
 (:import [java.util.concurrent ArrayBlockingQueue]))

(defprotocol MetricType
  (type-of [this])
  (value-of [this])
  (aggregation-value-of [this])
  (compute [this id timestamp metric tags]))

(defonce alert-type "nimrod.core.metric.Alert")
(defonce gauge-type "nimrod.core.metric.Gauge")
(defonce counter-type "nimrod.core.metric.Counter")
(defonce timer-type "nimrod.core.metric.Timer")

(deftype Alert [state]
  MetricType
  (type-of [this] alert-type)
  (value-of [this] (-> @state 
    (assoc :systemtime (date-to-string (clock)))))
  (aggregation-value-of [this] 0)
  (compute [this id timestamp alert tags]
    (if-let [current @state]
      (swap! state conj 
        {:timestamp (Long/parseLong timestamp)
         :alert alert
         :samples (inc (current :samples))
         :tags tags})
      (reset! state {:id id 
       :timestamp (Long/parseLong timestamp)
       :samples 1
       :alert alert
       :tags tags}))
    this))

(deftype Gauge [state]
  MetricType
  (type-of [this] gauge-type)
  (value-of [this] (-> @state 
    (assoc :systemtime (date-to-string (clock)))))
  (aggregation-value-of [this] (@state :gauge))
  (compute [this id timestamp gauge tags]
    (let [gauge (Long/parseLong gauge)]
      (if-let [current @state]
        (let [samples (inc (current :samples))]
        (swap! state conj
          {:timestamp (Long/parseLong timestamp)
           :gauge gauge
           :ewma (ewma (current :ewma) gauge samples)
           :samples samples
           :tags tags}))
        (reset! state {:id id
         :timestamp (Long/parseLong timestamp)
         :gauge gauge
         :ewma nil
         :samples 1
         :tags tags}))
      this)))

(deftype Counter [state]
  MetricType
  (type-of [this] counter-type)
  (value-of [this] (-> @state 
    (assoc :systemtime (date-to-string (clock)))))
  (aggregation-value-of [this] (@state :counter))
  (compute [this id timestamp increment tags]
    (let [increment (Long/parseLong increment)]
      (if-let [current @state]
        (let 
          [samples (inc (current :samples))
          previous-counter (current :counter)
          counter (+ previous-counter increment)]
          (swap! state conj 
            {:timestamp (Long/parseLong timestamp)
             :counter counter
             :ewma (ewma (current :ewma) counter samples)
             :samples samples
             :latest-increment increment
             :tags tags}))
        (reset! state {:id id
         :timestamp (Long/parseLong timestamp)
         :counter increment
         :ewma nil
         :samples 1
         :latest-increment increment
         :tags tags})))
    this))

(deftype Timer [state]
  MetricType
  (type-of [this] timer-type)
  (value-of [this] (-> @state 
    (assoc :systemtime (date-to-string (clock)))))
  (aggregation-value-of [this] (@state :elapsed-time))
  (compute [this id timestamp action tags]
    (let [timer (Long/parseLong timestamp)]
      (if-let [current @state]
        (cond
          (= "start" action)
          (swap! state conj {:timestamp timer :start timer :end 0 :elapsed-time 0 :tags tags})
          (= "stop" action)
          (let 
            [start (current :start)
            samples (inc (current :samples))
            elapsed (- timer start)]
            (swap! state conj 
              {:timestamp timer
               :end timer
               :elapsed-time elapsed
               :ewma (ewma (current :ewma) elapsed samples)
               :samples samples
               :tags tags}))
          :else (throw (IllegalStateException. (str "Bad timer action: " action))))
        (if (= "start" action)
          (reset! state {:id id :timestamp timer :start timer :end 0 :elapsed-time 0 :ewma nil :samples 0 :tags tags})
          (throw (IllegalStateException. (str "Bad timer action, first time must always be 'start', not: " action))))))
    this))

(defonce metrics (atom {}))

(defonce metrics-factory {
  alert-type #(Alert. (atom %1))
  gauge-type #(Gauge. (atom %1))
  counter-type #(Counter. (atom %1))
  timer-type #(Timer. (atom %1))
  })

(defn compute-metric [metric-ns metric-type metric-id timestamp value tags]
  (let 
    [metric (or (get-in @metrics [metric-ns metric-type metric-id]) ((metrics-factory metric-type) (read-metric @store metric-ns metric-type metric-id)))
    new-metric-value (value-of (compute metric metric-id timestamp value tags))]
    (try 
      (set-metric @store metric-ns metric-type metric-id new-metric-value (aggregation-value-of metric)) 
      (catch Exception ex (log/error ex (.getMessage ex))))))
