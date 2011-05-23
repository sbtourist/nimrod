(ns nimrod.core.metrics-test
 (:use
   [clojure.test]
   [nimrod.core.metrics])
 )

(defn- gauge [gauge-ns gauge-id]
  (if-let [g (get @gauges gauge-ns)]
    @(get g gauge-id)
    nil
    )
  )

(defn- gauges-in [gauge-ns]
  (vals (get @gauges gauge-ns))
  )

(defn- counter [counter-ns counter-id]
  (if-let [c (get @counters counter-ns)]
    @(get c counter-id)
    nil
    )
  )

(defn- counters-in [counter-ns]
  (vals (get @counters counter-ns))
  )

(defn- timer [timer-ns timer-id]
  (if-let [t (get @timers timer-ns)]
    @(get t timer-id)
    nil
    )
  )

(defn- timers-in [timer-ns]
  (vals (get @timers timer-ns))
  )

(deftest gauge-metrics
  (testing "Null gauge"
    (is (nil? (gauge "ns1" "1")))
    )
  (testing "Initial gauge value"
    (set-gauge "ns1" "1" "1" "v1")
    (apply await (gauges-in "ns1"))
    (is (not (nil? (gauge "ns1" "1"))))
    (is (= 1 ((gauge "ns1" "1") :timestamp)))
    (is (= "v1" ((gauge "ns1" "1") :value)))
    )
  (testing "Updated gauge value"
    (set-gauge "ns1" "1" "2" "v2")
    (apply await (gauges-in "ns1"))
    (is (not (nil? (gauge "ns1" "1"))))
    (is (= 2 ((gauge "ns1" "1") :timestamp)))
    (is (= "v2" ((gauge "ns1" "1") :value)))
    )
  )

(deftest counter-metrics
  (testing "Null counter"
    (is (nil? (counter "ns1" "1")))
    )
  (testing "Initial counter values"
    (set-counter "ns1" "1" "2" "4")
    (apply await (counters-in "ns1"))
    (is (not (nil? (counter "ns1" "1"))))
    (is (= 2 ((counter "ns1" "1") :timestamp)))
    (is (= 4 ((counter "ns1" "1") :value)))
    (is (= 4 ((counter "ns1" "1") :value-average)))
    (is (= 0 ((counter "ns1" "1") :value-variance)))
    (is (= 2 ((counter "ns1" "1") :interval-average)))
    (is (= 0 ((counter "ns1" "1") :interval-variance)))
    )
  (testing "Updated counter values"
    (set-counter "ns1" "1" "4" "6")
    (apply await (counters-in "ns1"))
    (is (not (nil? (counter "ns1" "1"))))
    (is (= 4 ((counter "ns1" "1") :timestamp)))
    (is (= 10 ((counter "ns1" "1") :value)))
    (is (= 5 ((counter "ns1" "1") :value-average)))
    (is (= 2 ((counter "ns1" "1") :value-variance)))
    (is (= 2 ((counter "ns1" "1") :interval-average)))
    (is (= 0 ((counter "ns1" "1") :interval-variance)))
    )
  )

(deftest timer-metrics
  (testing "Null timer"
    (is (nil? (timer "ns1" "1")))
    )
  (testing "Start timer"
    (set-timer "ns1" "1" "2" "2")
    (apply await (timers-in "ns1"))
    (is (not (nil? (timer "ns1" "1"))))
    (is (= 2 ((timer "ns1" "1") :start)))
    (is (= 0 ((timer "ns1" "1") :end)))
    (is (= 0 ((timer "ns1" "1") :elapsed-time)))
    (is (= 0 ((timer "ns1" "1") :elapsed-time-average)))
    (is (= 0 ((timer "ns1" "1") :elapsed-time-variance)))
    )
  (testing "Stop timer"
    (set-timer "ns1" "1" "4" "4")
    (apply await (timers-in "ns1"))
    (is (not (nil? (timer "ns1" "1"))))
    (is (= 2 ((timer "ns1" "1") :start)))
    (is (= 4 ((timer "ns1" "1") :end)))
    (is (= 2 ((timer "ns1" "1") :elapsed-time)))
    (is (= 2 ((timer "ns1" "1") :elapsed-time-average)))
    (is (= 0 ((timer "ns1" "1") :elapsed-time-variance)))
    )
  (testing "Restart timer"
    (set-timer "ns1" "1" "6" "6")
    (apply await (timers-in "ns1"))
    (is (not (nil? (timer "ns1" "1"))))
    (is (= 6 ((timer "ns1" "1") :start)))
    (is (= 0 ((timer "ns1" "1") :end)))
    (is (= 0 ((timer "ns1" "1") :elapsed-time)))
    (is (= 2 ((timer "ns1" "1") :elapsed-time-average)))
    (is (= 0 ((timer "ns1" "1") :elapsed-time-variance)))
    (set-timer "ns1" "1" "10" "10")
    (apply await (timers-in "ns1"))
    (is (not (nil? (timer "ns1" "1"))))
    (is (= 6 ((timer "ns1" "1") :start)))
    (is (= 10 ((timer "ns1" "1") :end)))
    (is (= 4 ((timer "ns1" "1") :elapsed-time)))
    (is (= 3 ((timer "ns1" "1") :elapsed-time-average)))
    (is (= 2 ((timer "ns1" "1") :elapsed-time-variance)))
    )
  )