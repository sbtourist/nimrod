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
        (alter gauges conj [id gauge])
        (notify-gauge gauge id timestamp value)
        )
      )
    )
  )

; ---

(defn- notify-counter [counter id timestamp value]
  (send counter #(if-let [state %1]
                   (let [previous-time (state :timestamp)
                         previous-value (state :value)
                         previous-interval-average (state :interval-average)
                         previous-value-average (state :value-average)
                         previous-interval-variance (state :interval-variance)
                         previous-value-variance (state :value-variance)
                         new-value (Long/parseLong value)
                         interval (- (Long/parseLong timestamp) previous-time)
                         samples (inc (state :samples))
                         interval-average (average samples previous-interval-average interval)
                         value-average (average samples previous-value-average new-value)
                         interval-variance (variance samples previous-interval-variance previous-interval-average interval-average interval)
                         value-variance (variance samples previous-value-variance previous-value-average value-average new-value)]
                     (conj state {:timestamp (Long/parseLong timestamp)
                                  :value (+ previous-value new-value)
                                  :samples samples
                                  :interval-average interval-average
                                  :value-average value-average
                                  :interval-variance interval-variance
                                  :value-variance value-variance
                                  })
                     )
                   {:id id 
                    :timestamp (Long/parseLong timestamp)
                    :value (Long/parseLong value)
                    :samples 1
                    :interval-average (Long/parseLong timestamp)
                    :interval-variance 0
                    :value-average (Long/parseLong value)
                    :value-variance 0}
                   )
    )
  )

(defn set-counter [id timestamp value]
  (dosync
    (if-let [counter (get @counters id)]
      (notify-counter counter id timestamp value)
      (let [counter (new-agent nil)]
        (alter counters conj [id counter])
        (notify-counter counter id timestamp value)
        )
      )
    )
  )

; ---

(defn- notify-timer [timer id timestamp value]
  (send timer #(if-let [state %1]
                 (if (= 0 (state :end))
                   (let [previous-elapsed-time-average (state :elapsed-time-average)
                         previous-elapsed-time-variance (state :elapsed-time-variance)
                         new-value (Long/parseLong value)
                         start (state :start)
                         samples (inc (state :samples))
                         elapsed-time (- new-value start)
                         elapsed-time-average (average samples previous-elapsed-time-average elapsed-time)
                         elapsed-time-variance (variance samples previous-elapsed-time-variance previous-elapsed-time-average elapsed-time-average elapsed-time)]
                     (conj state {:timestamp (Long/parseLong timestamp)
                                  :end new-value
                                  :elapsed-time elapsed-time
                                  :elapsed-time-average elapsed-time-average
                                  :elapsed-time-variance elapsed-time-variance
                                  :samples samples})
                     )
                   (conj state {:timestamp (Long/parseLong timestamp) :start (Long/parseLong value) :end 0 :elapsed-time 0})
                   )
                 {:id id :timestamp (Long/parseLong timestamp) :start (Long/parseLong value) :end 0 :elapsed-time 0 :elapsed-time-average 0 :elapsed-time-variance 0 :samples 0}
                 )
    )
  )

(defn set-timer [id timestamp value]
  (dosync
    (if-let [timer (get @timers id)]
      (notify-timer timer id timestamp value)
      (let [timer (new-agent nil)]
        (alter timers conj [id timer])
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