(ns nimrod.core.metrics
 (:use 
   [nimrod.core.stat]
   [nimrod.core.util])
 )

(defonce gauges (ref {}))
(defonce counters (ref {}))
(defonce timers (ref {}))

; ---

(defn- notify-gauge [gauge id timestamp value]
  (send gauge #(if-let [state %1]
                 (conj state {:timestamp (Long/parseLong timestamp) :value value})
                 {:id id :timestamp (Long/parseLong timestamp) :value value}
                 )
    )
  )

(defn set-gauge [id timestamp value]
  (dosync
    (if-let [gauge (get @gauges id)]
      (notify-gauge gauge id timestamp value)
      (let [gauge (new-agent nil)]
        (alter gauges assoc id gauge)
        (notify-gauge gauge id timestamp value)
        )
      )
    )
  )

; ---

(defn- notify-counter [counter id timestamp value]
  (let [n_timestamp (Long/parseLong timestamp) n_value (Long/parseLong value)]
    (send counter #(if-let [state %1]
                     (let [previous-time (state :timestamp)
                           previous-value (state :value)
                           previous-interval-average (state :interval-average)
                           previous-value-average (state :value-average)
                           previous-interval-variance (state :interval-variance)
                           previous-value-variance (state :value-variance)
                           interval (- n_timestamp previous-time)
                           samples (inc (state :samples))
                           interval-average (average samples previous-interval-average interval)
                           value-average (average samples previous-value-average n_value)
                           interval-variance (variance samples previous-interval-variance previous-interval-average interval-average interval)
                           value-variance (variance samples previous-value-variance previous-value-average value-average n_value)]
                       (conj state {:timestamp n_timestamp
                                    :value (+ previous-value n_value)
                                    :samples samples
                                    :interval-average interval-average
                                    :value-average value-average
                                    :interval-variance interval-variance
                                    :value-variance value-variance
                                    })
                       )
                     {:id id
                      :timestamp n_timestamp
                      :value n_value
                      :samples 1
                      :interval-average n_timestamp
                      :interval-variance 0
                      :value-average n_value
                      :value-variance 0}
                     )
      )
    )
  )

(defn set-counter [id timestamp value]
  (dosync
    (if-let [counter (get @counters id)]
      (notify-counter counter id timestamp value)
      (let [counter (new-agent nil)]
        (alter counters assoc id counter)
        (notify-counter counter id timestamp value)
        )
      )
    )
  )

; ---

(defn- notify-timer [timer id timestamp value]
  (let [n_timestamp (Long/parseLong timestamp) n_value (Long/parseLong value)]
  (send timer #(if-let [state %1]
                 (if (= 0 (state :end))
                   (let [previous-elapsed-time-average (state :elapsed-time-average)
                         previous-elapsed-time-variance (state :elapsed-time-variance)
                         start (state :start)
                         samples (inc (state :samples))
                         elapsed-time (- n_value start)
                         elapsed-time-average (average samples previous-elapsed-time-average elapsed-time)
                         elapsed-time-variance (variance samples previous-elapsed-time-variance previous-elapsed-time-average elapsed-time-average elapsed-time)]
                     (conj state {:timestamp n_timestamp
                                  :end n_value
                                  :elapsed-time elapsed-time
                                  :elapsed-time-average elapsed-time-average
                                  :elapsed-time-variance elapsed-time-variance
                                  :samples samples})
                     )
                   (conj state {:timestamp n_timestamp :start n_value :end 0 :elapsed-time 0})
                   )
                 {:id id :timestamp n_timestamp :start n_value :end 0 :elapsed-time 0 :elapsed-time-average 0 :elapsed-time-variance 0 :samples 0}
                 )
    )
  )
  )

(defn set-timer [id timestamp value]
  (dosync
    (if-let [timer (get @timers id)]
      (notify-timer timer id timestamp value)
      (let [timer (new-agent nil)]
        (alter timers assoc id timer)
        (notify-timer timer id timestamp value)
        )
      )
    )
  )

; ---

(defonce metrics {
                  "gauge" set-gauge
                  "counter" set-counter
                  "timer" set-timer
                  })