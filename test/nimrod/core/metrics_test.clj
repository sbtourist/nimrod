(ns nimrod.core.metrics-test
 (:use
   [clojure.test]
   [nimrod.core.metrics])
 )

(deftest gauge-metrics
  (testing "Null gauge"
    (is (nil? (get @gauges "1")))
    )
  (testing "Initial gauge value"
    (set-gauge "1" "1" "v1")
    (apply await (vals @gauges))
    (is (not (nil? (get @gauges "1"))))
    (is (= 1 (get @(get @gauges "1") :timestamp)))
    (is (= "v1" (get @(get @gauges "1") :value)))
    )
  (testing "Updated gauge value"
    (set-gauge "1" "2" "v2")
    (apply await (vals @gauges))
    (is (not (nil? (get @gauges "1"))))
    (is (= 2 (get @(get @gauges "1") :timestamp)))
    (is (= "v2" (get @(get @gauges "1") :value)))
    )
  )

(deftest counter-metrics
  (testing "Null counter"
    (is (nil? (get @counters "1")))
    )
  (testing "Initial counter values"
    (set-counter "1" "2" "4")
    (apply await (vals @counters))
    (is (not (nil? (get @counters "1"))))
    (is (= 2 (get @(get @counters "1") :timestamp)))
    (is (= 4 (get @(get @counters "1") :value)))
    (is (= 4 (get @(get @counters "1") :value-average)))
    (is (= 0 (get @(get @counters "1") :value-variance)))
    (is (= 2 (get @(get @counters "1") :interval-average)))
    (is (= 0 (get @(get @counters "1") :interval-variance)))
    )
  (testing "Updated counter values"
    (set-counter "1" "4" "6")
    (apply await (vals @counters))
    (is (not (nil? (get @counters "1"))))
    (is (= 4 (get @(get @counters "1") :timestamp)))
    (is (= 10 (get @(get @counters "1") :value)))
    (is (= 5 (get @(get @counters "1") :value-average)))
    (is (= 2 (get @(get @counters "1") :value-variance)))
    (is (= 2 (get @(get @counters "1") :interval-average)))
    (is (= 0 (get @(get @counters "1") :interval-variance)))
    )
  )

(deftest timer-metrics
  (testing "Null timer"
    (is (nil? (get @timers "1")))
    )
  (testing "Start timer"
    (set-timer "1" "2" "2")
    (apply await (vals @timers))
    (is (not (nil? (get @timers "1"))))
    (is (= 2 (get @(get @timers "1") :start)))
    (is (= 0 (get @(get @timers "1") :end)))
    (is (= 0 (get @(get @timers "1") :elapsed-time)))
    (is (= 0 (get @(get @timers "1") :elapsed-time-average)))
    (is (= 0 (get @(get @timers "1") :elapsed-time-variance)))
    )
  (testing "Stop timer"
    (set-timer "1" "4" "4")
    (apply await (vals @timers))
    (is (not (nil? (get @timers "1"))))
    (is (= 2 (get @(get @timers "1") :start)))
    (is (= 4 (get @(get @timers "1") :end)))
    (is (= 2 (get @(get @timers "1") :elapsed-time)))
    (is (= 2 (get @(get @timers "1") :elapsed-time-average)))
    (is (= 0 (get @(get @timers "1") :elapsed-time-variance)))
    )
  (testing "Restart timer"
    (set-timer "1" "6" "6")
    (apply await (vals @timers))
    (is (not (nil? (get @timers "1"))))
    (is (= 6 (get @(get @timers "1") :start)))
    (is (= 0 (get @(get @timers "1") :end)))
    (is (= 0 (get @(get @timers "1") :elapsed-time)))
    (is (= 2 (get @(get @timers "1") :elapsed-time-average)))
    (is (= 0 (get @(get @timers "1") :elapsed-time-variance)))
    (set-timer "1" "10" "10")
    (apply await (vals @timers))
    (is (not (nil? (get @timers "1"))))
    (is (= 6 (get @(get @timers "1") :start)))
    (is (= 10 (get @(get @timers "1") :end)))
    (is (= 4 (get @(get @timers "1") :elapsed-time)))
    (is (= 3 (get @(get @timers "1") :elapsed-time-average)))
    (is (= 2 (get @(get @timers "1") :elapsed-time-variance)))
    )
  )