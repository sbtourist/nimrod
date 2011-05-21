(ns nimrod.core.metrics
 (:use 
   [nimrod.core.stat]
   [nimrod.core.util])
 )

(defonce gauges (ref {}))
(defonce counters (ref {}))
(defonce timers (ref {}))

; ---

(defn- notify-gauge [gauge id value update-time]
  (send gauge #(if-let [state %1]
                 (conj state {:update update-time :value value})
                 {:id id :start update-time :update update-time :value value}
                 )
    )
  )

(defn set-gauge [id value]
  (let [update-time (date-in-millis)]
    (dosync
      (if-let [gauge (get @gauges id)]
        (notify-gauge gauge id value update-time)
        (let [gauge (new-agent nil)]
          (alter gauges conj [id gauge])
          (notify-gauge gauge id value update-time)
          )
        )
      )
    )
  )

; ---

(defn- notify-counter [counter id value update-time]
  (send counter #(if-let [state %1]
                   (let [previous-time (state :update)
                         previous-value (state :value)
                         previous-interval-average (state :interval-average)
                         previous-value-average (state :value-average)
                         previous-interval-std-deviation (state :interval-std-deviation)
                         previous-value-std-deviation (state :value-std-deviation)
                         interval (- update-time previous-time)
                         samples (inc (state :samples))
                         interval-average (average samples previous-interval-average interval)
                         value-average (average samples previous-value-average value)
                         interval-std-deviation (std-deviation samples previous-interval-std-deviation previous-interval-average interval-average interval)
                         value-std-deviation (std-deviation samples previous-value-std-deviation previous-value-average value-average value)]
                     (conj state {:update update-time
                                  :value (+ previous-value value)
                                  :samples samples
                                  :interval-average interval-average
                                  :value-average value-average
                                  :interval-std-deviation interval-std-deviation
                                  :value-std-deviation value-std-deviation
                                  })
                     )
                   {:id id 
                    :start update-time
                    :update update-time
                    :value value
                    :samples 1
                    :interval-average 0
                    :interval-std-deviation 0
                    :value-average value
                    :value-std-deviation 0}
                   )
    )
  )

(defn set-counter [id value]
  (let [update-time (date-in-millis)]
    (dosync
      (if-let [counter (get @counters id)]
        (notify-counter counter id value update-time)
        (let [counter (new-agent nil)]
          (alter counters conj [id counter])
          (notify-counter counter id value update-time)
          )
        )
      )
    )
  )

; ---

(defn- notify-timer [timer id value]
  (send timer #(if-let [state %1]
                 (if (= 0 (state :end))
                   (let [previous-elapsed-time-average (state :elapsed-time-average)
                         previous-elapsed-time-std-deviation (state :elapsed-time-std-deviation)
                         start (state :start)
                         samples (inc (state :samples))
                         elapsed-time (- value start)
                         elapsed-time-average (average samples previous-elapsed-time-average elapsed-time)
                         elapsed-time-std-deviation (std-deviation samples previous-elapsed-time-std-deviation previous-elapsed-time-average elapsed-time-average elapsed-time)]
                     (conj state {:end value
                                  :elapsed-time elapsed-time
                                  :elapsed-time-average elapsed-time-average
                                  :elapsed-time-std-deviation elapsed-time-std-deviation
                                  :samples samples})
                     )
                   (conj state {:start value :end 0 :update 0 :elapsed-time 0})
                   )
                 {:id id :start value :end 0 :elapsed-time 0 :elapsed-time-average 0 :elapsed-time-std-deviation 0 :samples 0}
                 )
    )
  )

(defn set-timer [id value]
  (dosync
    (if-let [timer (get @timers id)]
      (notify-timer timer id value)
      (let [timer (new-agent nil)]
        (alter timers conj [id timer])
        (notify-timer timer id value)
        )
      )
    )
  )