(ns nimrod.core.metrics-test
 (:use
   [clojure.test]
   [nimrod.core.metrics])
 )

(deftest gauge-metrics
  (is (nil? (get @gauges "1")))
  (set-gauge "1" "v1")
  (apply await (vals @gauges))
  (is (not (nil? (get @gauges "1"))))
  (is (= "v1" (get @(get @gauges "1") :value)))
  (set-gauge "1" "v2")
  (apply await (vals @gauges))
  (is (not (nil? (get @gauges "1"))))
  (is (= "v2" (get @(get @gauges "1") :value)))
  )

(deftest counter-metrics
  (is (nil? (get @counters "1")))
  (set-counter "1" 1)
  (apply await (vals @counters))
  (is (not (nil? (get @counters "1"))))
  (is (= 1 (get @(get @counters "1") :value)))
  (set-counter "1" 3)
  (apply await (vals @counters))
  (is (not (nil? (get @counters "1"))))
  (is (= 4 (get @(get @counters "1") :value)))
  )

(deftest timer-metrics
  (is (nil? (get @timers "1")))
  (set-timer "1" 1)
  (apply await (vals @timers))
  (is (not (nil? (get @timers "1"))))
  (is (= 0 (get @(get @timers "1") :elapsed-time)))
  (set-timer "1" 3)
  (apply await (vals @timers))
  (is (not (nil? (get @timers "1"))))
  (is (= 2 (get @(get @timers "1") :elapsed-time)))
  )