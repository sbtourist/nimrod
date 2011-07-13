(ns nimrod.core.metrics-test
 (:use
   [clojure.test]
   [nimrod.core.metrics])
 )

(defn- read-status [status-ns status-id]
  (read-metric (metric-types :status) status-ns status-id)
  )

(defn- update-status
  ([status-ns status-id timestamp value]
    (set-metric (metric-types :status) status-ns status-id timestamp value #{}))
  ([status-ns status-id timestamp value tags]
    (set-metric (metric-types :status) status-ns status-id timestamp value tags))
  )

(defn- list-statuses [status-ns tags]
  (list-metrics (metric-types :status) status-ns tags)
  )

(defn- remove-status [status-ns status-id]
  (remove-metric (metric-types :status) status-ns status-id)
  )

(defn- remove-statuses [status-ns tags]
  (remove-metrics (metric-types :status) status-ns tags)
  )

(defn- expire-statuses [status-ns age]
  (expire-metrics (metric-types :status) status-ns age)
  )

(defn- read-status-history
  ([status-ns status-id]
    ((read-history (metric-types :status) status-ns status-id nil) :values)
    )
  ([status-ns status-id tags]
    ((read-history (metric-types :status) status-ns status-id tags) :values)
    )
  )

(defn- reset-status-history [status-ns status-id limit]
  (reset-history (metric-types :status) status-ns status-id limit)
  )

(defn- read-gauge [gauge-ns gauge-id]
  (read-metric (metric-types :gauge) gauge-ns gauge-id)
  )

(defn- update-gauge
  ([gauge-ns gauge-id timestamp value]
    (set-metric (metric-types :gauge) gauge-ns gauge-id timestamp value #{}))
  ([gauge-ns gauge-id timestamp value tags]
    (set-metric (metric-types :gauge) gauge-ns gauge-id timestamp value tags))
  )

(defn- list-gauges [gauge-ns tags]
  (list-metrics (metric-types :gauge) gauge-ns tags)
  )

(defn- read-gauge-history
  ([gauge-ns gauge-id]
    ((read-history (metric-types :gauge) gauge-ns gauge-id nil) :values)
    )
  ([gauge-ns gauge-id tags]
    ((read-history (metric-types :gauge) gauge-ns gauge-id tags) :values)
    )
  )

(defn- reset-gauge-history [gauge-ns gauge-id limit]
  (reset-history (metric-types :gauge) gauge-ns gauge-id limit)
  )

(defn- remove-gauge [gauge-ns gauge-id]
  (remove-metric (metric-types :gauge) gauge-ns gauge-id)
  )

(defn- remove-gauges [gauge-ns tags]
  (remove-metrics (metric-types :gauge) gauge-ns tags)
  )

(defn- expire-gauges [gauge-ns age]
  (expire-metrics (metric-types :gauge) gauge-ns age)
  )

(defn- read-counter [counter-ns counter-id]
  (read-metric (metric-types :counter) counter-ns counter-id)
  )

(defn- update-counter 
  ([counter-ns counter-id timestamp value]
    (set-metric (metric-types :counter) counter-ns counter-id timestamp value #{}))
  ([counter-ns counter-id timestamp value tags]
    (set-metric (metric-types :counter) counter-ns counter-id timestamp value tags))
  )

(defn- list-counters [counter-ns tags]
  (list-metrics (metric-types :counter) counter-ns tags)
  )

(defn- remove-counter [counter-ns counter-id]
  (remove-metric (metric-types :counter) counter-ns counter-id)
  )

(defn- remove-counters [counter-ns tags]
  (remove-metrics (metric-types :counter) counter-ns tags)
  )

(defn- expire-counters [counter-ns age]
  (expire-metrics (metric-types :counter) counter-ns age)
  )

(defn- read-counter-history
  ([counter-ns counter-id]
    ((read-history (metric-types :counter) counter-ns counter-id nil) :values)
    )
  ([counter-ns counter-id tags]
    ((read-history (metric-types :counter) counter-ns counter-id tags) :values)
    )
  )

(defn- reset-counter-history [counter-ns counter-id limit]
  (reset-history (metric-types :counter) counter-ns counter-id limit)
  )

(defn- read-timer [timer-ns timer-id]
  (read-metric (metric-types :timer) timer-ns timer-id)
  )

(defn- update-timer 
  ([timer-ns timer-id timestamp value]
    (set-metric (metric-types :timer) timer-ns timer-id timestamp value #{}))
  ([timer-ns timer-id timestamp value tags]
    (set-metric (metric-types :timer) timer-ns timer-id timestamp value tags))
  )

(defn- list-timers [timer-ns tags]
  (list-metrics (metric-types :timer) timer-ns tags)
  )

(defn- remove-timer [timer-ns timer-id]
  (remove-metric (metric-types :timer) timer-ns timer-id)
  )

(defn- remove-timers [timer-ns tags]
  (remove-metrics (metric-types :timer) timer-ns tags)
  )

(defn- expire-timers [timer-ns age]
  (expire-metrics (metric-types :timer) timer-ns age)
  )

(defn- read-timer-history
  ([timer-ns timer-id]
    ((read-history (metric-types :timer) timer-ns timer-id nil) :values)
    )
  ([timer-ns timer-id tags]
    ((read-history (metric-types :timer) timer-ns timer-id tags) :values)
    )
  )

(defn- reset-timer-history [timer-ns timer-id limit]
  (reset-history (metric-types :timer) timer-ns timer-id limit)
  )

(deftest status-metrics
  (testing "Null status"
    (is (nil? (read-status "status-metrics" "1")))
    )
  (testing "Initial status value"
    (update-status "status-metrics" "1" "1" "v1")
    (is (not (nil? (read-status "status-metrics" "1"))))
    (is (= 1 ((read-status "status-metrics" "1") :timestamp)))
    (is (= "v1" ((read-status "status-metrics" "1") :status)))
    )
  (testing "Updated status value"
    (update-status "status-metrics" "1" "2" "v2")
    (is (not (nil? (read-status "status-metrics" "1"))))
    (is (= 2 ((read-status "status-metrics" "1") :timestamp)))
    (is (= "v2" ((read-status "status-metrics" "1") :status)))
    )
  (testing "List and remove statuses"
    (is (= ["1"] (list-statuses "status-metrics" nil)))
    (remove-status "status-metrics" "1")
    (is (= [] (list-statuses "status-metrics" nil)))
    )
  (testing "List and remove statuses with tags"
    (update-status "status-metrics" "2" "3" "v3" #{"tag"})
    (is (= ["2"] (list-statuses "status-metrics" #{"tag"})))
    (is (= [] (list-statuses "status-metrics" #{"notag"})))
    (remove-statuses "status-metrics" #{"tag"})
    (is (= [] (list-statuses "status-metrics" #{"tag"})))
    )
  (testing "Expire statuses"
    (update-status "status-metrics" "3" "4" "v4")
    (Thread/sleep 200)
    (update-status "status-metrics" "4" "5" "v5")
    (Thread/sleep 100)
    (expire-statuses "status-metrics" 200)
    (is (= ["4"] (list-statuses "status-metrics" nil)))
    )
  )

(deftest status-history
  (testing "Reset status history"
    (reset-status-history "status-history" "1" 2)
    )
  (testing "Status history under limit"
    (update-status "status-history" "1" "1" "v1")
    (update-status "status-history" "1" "2" "v2")
    (is (= 1 ((first (read-status-history "status-history" "1")) :timestamp)))
    (is (= 2 ((second (read-status-history "status-history" "1")) :timestamp)))
    )
  (testing "Status history over limit"
    (update-status "status-history" "1" "3" "v3")
    (is (= 2 ((first (read-status-history "status-history" "1")) :timestamp)))
    (is (= 3 ((second (read-status-history "status-history" "1")) :timestamp)))
    )
  )

(deftest status-history-with-tags
  (testing "Status history with tags"
    (update-status "status-history-with-tags" "1" "1" "v1" #{"tag1", "tag2"})
    (update-status "status-history-with-tags" "1" "2" "v2" #{"tag3"})
    (is (= 1 (count (read-status-history "status-history-with-tags" "1" #{"tag1", "tag2"}))))
    (is (= 1 ((first (read-status-history "status-history-with-tags" "1" #{"tag1", "tag2"})) :timestamp)))
    )
  )

(deftest gauge-metrics
  (testing "Null gauge"
    (is (nil? (read-gauge "gauge-metrics" "1")))
    )
  (testing "Initial gauge values"
    (update-gauge "gauge-metrics" "1" "2" "4")
    (is (not (nil? (read-gauge "gauge-metrics" "1"))))
    (is (= 2 ((read-gauge "gauge-metrics" "1") :timestamp)))
    (is (= 4 ((read-gauge "gauge-metrics" "1") :gauge)))
    (is (= 4 ((read-gauge "gauge-metrics" "1") :gauge-average)))
    (is (= 0 ((read-gauge "gauge-metrics" "1") :gauge-variance)))
    (is (= 0 ((read-gauge "gauge-metrics" "1") :interval-average)))
    (is (= 0 ((read-gauge "gauge-metrics" "1") :interval-variance)))
    )
  (testing "Updated gauge values"
    (update-gauge "gauge-metrics" "1" "4" "6")
    (is (not (nil? (read-gauge "gauge-metrics" "1"))))
    (is (= 4 ((read-gauge "gauge-metrics" "1") :timestamp)))
    (is (= 6 ((read-gauge "gauge-metrics" "1") :gauge)))
    (is (= 5 ((read-gauge "gauge-metrics" "1") :gauge-average)))
    (is (= 2 ((read-gauge "gauge-metrics" "1") :gauge-variance)))
    (is (= 2 ((read-gauge "gauge-metrics" "1") :interval-average)))
    (is (= 0 ((read-gauge "gauge-metrics" "1") :interval-variance)))
    )
  (testing "List and remove gauges"
    (is (= ["1"] (list-gauges "gauge-metrics" nil)))
    (remove-gauge "gauge-metrics" "1")
    (is (= [] (list-gauges "gauge-metrics" nil)))
    )
  (testing "List and remove gauges with tags"
    (update-gauge "gauge-metrics" "2" "1" "1" #{"tag"})
    (is (= ["2"] (list-gauges "gauge-metrics" #{"tag"})))
    (is (= [] (list-gauges "gauge-metrics" #{"notag"})))
    (remove-gauges "gauge-metrics" #{"tag"})
    (is (= [] (list-gauges "gauge-metrics" #{"tag"})))
    )
  (testing "Expire gauges"
    (update-gauge "gauge-metrics" "3" "1" "1")
    (Thread/sleep 200)
    (update-gauge "gauge-metrics" "4" "1" "1")
    (Thread/sleep 100)
    (expire-gauges "gauge-metrics" 200)
    (is (= ["4"] (list-gauges "gauge-metrics" nil)))    
    )
  )

(deftest gauge-history
  (testing "Reset gauge history"
    (reset-gauge-history "gauge-history" "1" 2)
    )
  (testing "Gauge history under limit"
    (update-gauge "gauge-history" "1" "1" "1")
    (update-gauge "gauge-history" "1" "2" "2")
    (is (= 1 ((first (read-gauge-history "gauge-history" "1")) :timestamp)))
    (is (= 2 ((second (read-gauge-history "gauge-history" "1")) :timestamp)))
    )
  (testing "Gauge history over limit"
    (update-gauge "gauge-history" "1" "3" "3")
    (is (= 2 ((first (read-gauge-history "gauge-history" "1")) :timestamp)))
    (is (= 3 ((second (read-gauge-history "gauge-history" "1")) :timestamp)))
    )
  )

(deftest gauge-history-with-tags
  (testing "Gauge history with tags"
    (update-gauge "gauge-history-with-tags" "1" "1" "1" #{"tag1", "tag2"})
    (update-gauge "gauge-history-with-tags" "1" "2" "2" #{"tag3"})
    (is (= 1 (count (read-gauge-history "gauge-history-with-tags" "1" #{"tag1", "tag2"}))))
    (is (= 1 ((first (read-gauge-history "gauge-history-with-tags" "1" #{"tag1", "tag2"})) :timestamp)))
    )
  )

(deftest counter-metrics
  (testing "Null counter"
    (is (nil? (read-counter "counter-metrics" "1")))
    )
  (testing "Initial counter values"
    (update-counter "counter-metrics" "1" "2" "4")
    (is (not (nil? (read-counter "counter-metrics" "1"))))
    (is (= 2 ((read-counter "counter-metrics" "1") :timestamp)))
    (is (= 4 ((read-counter "counter-metrics" "1") :counter)))
    (is (= 4 ((read-counter "counter-metrics" "1") :increment-average)))
    (is (= 0 ((read-counter "counter-metrics" "1") :increment-variance)))
    (is (= 4 ((read-counter "counter-metrics" "1") :latest-increment)))
    (is (= 0 ((read-counter "counter-metrics" "1") :interval-average)))
    (is (= 0 ((read-counter "counter-metrics" "1") :interval-variance)))
    (is (= 0 ((read-counter "counter-metrics" "1") :latest-interval)))
    )
  (testing "Updated counter values"
    (update-counter "counter-metrics" "1" "4" "6")
    (is (not (nil? (read-counter "counter-metrics" "1"))))
    (is (= 4 ((read-counter "counter-metrics" "1") :timestamp)))
    (is (= 10 ((read-counter "counter-metrics" "1") :counter)))
    (is (= 5 ((read-counter "counter-metrics" "1") :increment-average)))
    (is (= 2 ((read-counter "counter-metrics" "1") :increment-variance)))
    (is (= 6 ((read-counter "counter-metrics" "1") :latest-increment)))
    (is (= 2 ((read-counter "counter-metrics" "1") :interval-average)))
    (is (= 0 ((read-counter "counter-metrics" "1") :interval-variance)))
    (is (= 2 ((read-counter "counter-metrics" "1") :latest-interval)))
    )
  (testing "List and remove counters"
    (is (= ["1"] (list-counters "counter-metrics" nil)))
    (remove-counter "counter-metrics" "1")
    (is (= [] (list-counters "counter-metrics" nil)))
    )
  (testing "List and remove counters with tags"
    (update-counter "counter-metrics" "2" "1" "1" #{"tag"})
    (is (= ["2"] (list-counters "counter-metrics" #{"tag"})))
    (is (= [] (list-counters "counter-metrics" #{"notag"})))
    (remove-counters "counter-metrics" #{"tag"})
    (is (= [] (list-counters "counter-metrics" #{"tag"})))
    )
  (testing "Expire counters"
    (update-counter "counter-metrics" "3" "1" "1")
    (Thread/sleep 200)
    (update-counter "counter-metrics" "4" "1" "1")
    (Thread/sleep 100)
    (expire-counters "counter-metrics" 200)
    (is (= ["4"] (list-counters "counter-metrics" nil)))
    )
  )

(deftest counter-history
  (testing "Reset counter history"
    (reset-counter-history "counter-history" "1" 2)
    )
  (testing "Counter history under limit"
    (update-counter "counter-history" "1" "1" "1")
    (update-counter "counter-history" "1" "2" "2")
    (is (= 1 ((first (read-counter-history "counter-history" "1")) :timestamp)))
    (is (= 2 ((second (read-counter-history "counter-history" "1")) :timestamp)))
    )
  (testing "Counter history over limit"
    (update-counter "counter-history" "1" "3" "3")
    (is (= 2 ((first (read-counter-history "counter-history" "1")) :timestamp)))
    (is (= 3 ((second (read-counter-history "counter-history" "1")) :timestamp)))
    )
  )

(deftest counter-history-with-tags
  (testing "Counter history with tags"
    (update-counter "counter-history-with-tags" "1" "1" "1" #{"tag1", "tag2"})
    (update-counter "counter-history-with-tags" "1" "2" "2" #{"tag3"})
    (is (= 1 (count (read-counter-history "counter-history-with-tags" "1" #{"tag1", "tag2"}))))
    (is (= 1 ((first (read-counter-history "counter-history-with-tags" "1" #{"tag1", "tag2"})) :timestamp)))
    )
  )

(deftest timer-metrics
  (testing "Null timer"
    (is (nil? (read-timer "timer-metrics" "1")))
    )
  (testing "Start timer"
    (update-timer "timer-metrics" "1" "2" "start")
    (is (not (nil? (read-timer "timer-metrics" "1"))))
    (is (= 2 ((read-timer "timer-metrics" "1") :start)))
    (is (= 0 ((read-timer "timer-metrics" "1") :end)))
    (is (= 0 ((read-timer "timer-metrics" "1") :elapsed-time)))
    (is (= 0 ((read-timer "timer-metrics" "1") :elapsed-time-average)))
    (is (= 0 ((read-timer "timer-metrics" "1") :elapsed-time-variance)))
    )
  (testing "Stop timer"
    (update-timer "timer-metrics" "1" "4" "stop")
    (is (not (nil? (read-timer "timer-metrics" "1"))))
    (is (= 2 ((read-timer "timer-metrics" "1") :start)))
    (is (= 4 ((read-timer "timer-metrics" "1") :end)))
    (is (= 2 ((read-timer "timer-metrics" "1") :elapsed-time)))
    (is (= 2 ((read-timer "timer-metrics" "1") :elapsed-time-average)))
    (is (= 0 ((read-timer "timer-metrics" "1") :elapsed-time-variance)))
    )
  (testing "Restart timer"
    (update-timer "timer-metrics" "1" "6" "start")
    (is (not (nil? (read-timer "timer-metrics" "1"))))
    (is (= 6 ((read-timer "timer-metrics" "1") :start)))
    (is (= 0 ((read-timer "timer-metrics" "1") :end)))
    (is (= 0 ((read-timer "timer-metrics" "1") :elapsed-time)))
    (is (= 2 ((read-timer "timer-metrics" "1") :elapsed-time-average)))
    (is (= 0 ((read-timer "timer-metrics" "1") :elapsed-time-variance)))
    (update-timer "timer-metrics" "1" "10" "stop")
    (is (not (nil? (read-timer "timer-metrics" "1"))))
    (is (= 6 ((read-timer "timer-metrics" "1") :start)))
    (is (= 10 ((read-timer "timer-metrics" "1") :end)))
    (is (= 4 ((read-timer "timer-metrics" "1") :elapsed-time)))
    (is (= 3 ((read-timer "timer-metrics" "1") :elapsed-time-average)))
    (is (= 2 ((read-timer "timer-metrics" "1") :elapsed-time-variance)))
    )
  (testing "List and remove timers"
    (is (= ["1"] (list-timers "timer-metrics" nil)))
    (remove-timer "timer-metrics" "1")
    (is (= [] (list-timers "timer-metrics" nil)))
    )
  (testing "List and remove timers with tags"
    (update-timer "timer-metrics" "2" "1" "start" #{"tag"})
    (is (= ["2"] (list-timers "timer-metrics" #{"tag"})))
    (is (= [] (list-timers "timer-metrics" #{"notag"})))
    (remove-timers "timer-metrics" #{"tag"})
    (is (= [] (list-timers "timer-metrics" nil)))
    )
  (testing "Expire timers"
    (update-timer "timer-metrics" "3" "1" "start")
    (Thread/sleep 200)
    (update-timer "timer-metrics" "4" "1" "start")
    (Thread/sleep 100)
    (expire-timers "timer-metrics" 200)
    (is (= ["4"] (list-timers "timer-metrics" nil)))
    )
  )

(deftest timer-history
  (testing "Reset timer history"
    (reset-timer-history "timer-history" "1" 2)
    )
  (testing "Timer history under limit"
    (update-timer "timer-history" "1" "1" "start")
    (update-timer "timer-history" "1" "2" "stop")
    (is (= 1 ((first (read-timer-history "timer-history" "1")) :timestamp)))
    (is (= 2 ((second (read-timer-history "timer-history" "1")) :timestamp)))
    )
  (testing "Timer history over limit"
    (update-timer "timer-history" "1" "3" "start")
    (is (= 2 ((first (read-timer-history "timer-history" "1")) :timestamp)))
    (is (= 3 ((second (read-timer-history "timer-history" "1")) :timestamp)))
    )
  )

(deftest timer-history-with-tags
  (testing "Timer history with tags"
    (update-timer "timer-history-with-tags" "1" "1" "start" #{"tag1", "tag2"})
    (update-timer "timer-history-with-tags" "1" "2" "stop" #{"tag3"})
    (is (= 1 (count (read-timer-history "timer-history-with-tags" "1" #{"tag1", "tag2"}))))
    (is (= 1 ((first (read-timer-history "timer-history-with-tags" "1" #{"tag1", "tag2"})) :timestamp)))
    )
  )