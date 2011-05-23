(ns nimrod.core.metrics-test
 (:use
   [clojure.test]
   [nimrod.core.metrics])
 )

(defn- read-gauge [gauge-ns gauge-id]
  (read-metric (metrics :gauges) gauge-ns gauge-id)
  )

(defn- update-gauge [gauge-ns gauge-id timestamp value]
  (set-metric (metrics :gauges) gauge-ns gauge-id timestamp value)
  )

(defn- gauges-in [gauge-ns]
  (vals (get @gauges gauge-ns))
  )

(defn- read-counter [counter-ns counter-id]
  (read-metric (metrics :counters) counter-ns counter-id)
  )

(defn- update-counter [counter-ns counter-id timestamp value]
  (set-metric (metrics :counters) counter-ns counter-id timestamp value)
  )

(defn- counters-in [counter-ns]
  (vals (get @counters counter-ns))
  )

(defn- read-timer [timer-ns timer-id]
  (read-metric (metrics :timers) timer-ns timer-id)
  )

(defn- update-timer [timer-ns timer-id timestamp value]
  (set-metric (metrics :timers) timer-ns timer-id timestamp value)
  )

(defn- timers-in [timer-ns]
  (vals (get @timers timer-ns))
  )

(deftest gauge-metrics
  (testing "Null gauge"
    (is (nil? (read-gauge "ns1" "1")))
    )
  (testing "Initial gauge value"
    (update-gauge "ns1" "1" "1" "v1")
    (apply await (gauges-in "ns1"))
    (is (not (nil? (read-gauge "ns1" "1"))))
    (is (= 1 ((read-gauge "ns1" "1") :timestamp)))
    (is (= "v1" ((read-gauge "ns1" "1") :value)))
    )
  (testing "Updated gauge value"
    (update-gauge "ns1" "1" "2" "v2")
    (apply await (gauges-in "ns1"))
    (is (not (nil? (read-gauge "ns1" "1"))))
    (is (= 2 ((read-gauge "ns1" "1") :timestamp)))
    (is (= "v2" ((read-gauge "ns1" "1") :value)))
    )
  )

(deftest counter-metrics
  (testing "Null counter"
    (is (nil? (read-counter "ns1" "1")))
    )
  (testing "Initial counter values"
    (update-counter "ns1" "1" "2" "4")
    (apply await (counters-in "ns1"))
    (is (not (nil? (read-counter "ns1" "1"))))
    (is (= 2 ((read-counter "ns1" "1") :timestamp)))
    (is (= 4 ((read-counter "ns1" "1") :value)))
    (is (= 4 ((read-counter "ns1" "1") :value-average)))
    (is (= 0 ((read-counter "ns1" "1") :value-variance)))
    (is (= 2 ((read-counter "ns1" "1") :interval-average)))
    (is (= 0 ((read-counter "ns1" "1") :interval-variance)))
    )
  (testing "Updated counter values"
    (update-counter "ns1" "1" "4" "6")
    (apply await (counters-in "ns1"))
    (is (not (nil? (read-counter "ns1" "1"))))
    (is (= 4 ((read-counter "ns1" "1") :timestamp)))
    (is (= 10 ((read-counter "ns1" "1") :value)))
    (is (= 5 ((read-counter "ns1" "1") :value-average)))
    (is (= 2 ((read-counter "ns1" "1") :value-variance)))
    (is (= 2 ((read-counter "ns1" "1") :interval-average)))
    (is (= 0 ((read-counter "ns1" "1") :interval-variance)))
    )
  )

(deftest timer-metrics
  (testing "Null timer"
    (is (nil? (read-timer "ns1" "1")))
    )
  (testing "Start timer"
    (update-timer "ns1" "1" "2" "2")
    (apply await (timers-in "ns1"))
    (is (not (nil? (read-timer "ns1" "1"))))
    (is (= 2 ((read-timer "ns1" "1") :start)))
    (is (= 0 ((read-timer "ns1" "1") :end)))
    (is (= 0 ((read-timer "ns1" "1") :elapsed-time)))
    (is (= 0 ((read-timer "ns1" "1") :elapsed-time-average)))
    (is (= 0 ((read-timer "ns1" "1") :elapsed-time-variance)))
    )
  (testing "Stop timer"
    (update-timer "ns1" "1" "4" "4")
    (apply await (timers-in "ns1"))
    (is (not (nil? (read-timer "ns1" "1"))))
    (is (= 2 ((read-timer "ns1" "1") :start)))
    (is (= 4 ((read-timer "ns1" "1") :end)))
    (is (= 2 ((read-timer "ns1" "1") :elapsed-time)))
    (is (= 2 ((read-timer "ns1" "1") :elapsed-time-average)))
    (is (= 0 ((read-timer "ns1" "1") :elapsed-time-variance)))
    )
  (testing "Restart timer"
    (update-timer "ns1" "1" "6" "6")
    (apply await (timers-in "ns1"))
    (is (not (nil? (read-timer "ns1" "1"))))
    (is (= 6 ((read-timer "ns1" "1") :start)))
    (is (= 0 ((read-timer "ns1" "1") :end)))
    (is (= 0 ((read-timer "ns1" "1") :elapsed-time)))
    (is (= 2 ((read-timer "ns1" "1") :elapsed-time-average)))
    (is (= 0 ((read-timer "ns1" "1") :elapsed-time-variance)))
    (update-timer "ns1" "1" "10" "10")
    (apply await (timers-in "ns1"))
    (is (not (nil? (read-timer "ns1" "1"))))
    (is (= 6 ((read-timer "ns1" "1") :start)))
    (is (= 10 ((read-timer "ns1" "1") :end)))
    (is (= 4 ((read-timer "ns1" "1") :elapsed-time)))
    (is (= 3 ((read-timer "ns1" "1") :elapsed-time-average)))
    (is (= 2 ((read-timer "ns1" "1") :elapsed-time-variance)))
    )
  )