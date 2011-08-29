(ns nimrod.core.metrics-test
 (:use
   [clojure.test]
   [nimrod.core.metrics])
 )

(defn- read-alert [alert-ns alert-id]
  (read-metric (store :alerts) alert-ns alert-id)
  )

(defn- update-alert
  ([alert-ns alert-id timestamp value]
    (set-metric (store :alerts) alert-ns alert-id timestamp value #{})
    (Thread/sleep 250))
  ([alert-ns alert-id timestamp value tags]
    (set-metric (store :alerts) alert-ns alert-id timestamp value tags)
    (Thread/sleep 250))
  )

(defn- list-alerts [alert-ns tags]
  (list-metrics (store :alerts) alert-ns tags)
  )

(defn- remove-alert [alert-ns alert-id]
  (remove-metric (store :alerts) alert-ns alert-id)
  )

(defn- remove-alerts [alert-ns tags]
  (remove-metrics (store :alerts) alert-ns tags)
  )

(defn- expire-alerts [alert-ns age]
  (expire-metrics (store :alerts) alert-ns age)
  )

(defn- read-alert-history
  ([alert-ns alert-id]
    ((read-history (store :alerts) alert-ns alert-id nil) :values)
    )
  ([alert-ns alert-id tags]
    ((read-history (store :alerts) alert-ns alert-id tags) :values)
    )
  )

(defn- reset-alert-history [alert-ns alert-id limit]
  (reset-history (store :alerts) alert-ns alert-id limit)
  )

(defn- read-gauge [gauge-ns gauge-id]
  (read-metric (store :gauges) gauge-ns gauge-id)
  )

(defn- update-gauge
  ([gauge-ns gauge-id timestamp value]
    (set-metric (store :gauges) gauge-ns gauge-id timestamp value #{})
    (Thread/sleep 250))
  ([gauge-ns gauge-id timestamp value tags]
    (set-metric (store :gauges) gauge-ns gauge-id timestamp value tags)
    (Thread/sleep 250))
  )

(defn- list-gauges [gauge-ns tags]
  (list-metrics (store :gauges) gauge-ns tags)
  )

(defn- read-gauge-history
  ([gauge-ns gauge-id]
    ((read-history (store :gauges) gauge-ns gauge-id nil) :values)
    )
  ([gauge-ns gauge-id tags]
    ((read-history (store :gauges) gauge-ns gauge-id tags) :values)
    )
  )

(defn- reset-gauge-history [gauge-ns gauge-id limit]
  (reset-history (store :gauges) gauge-ns gauge-id limit)
  )

(defn- remove-gauge [gauge-ns gauge-id]
  (remove-metric (store :gauges) gauge-ns gauge-id)
  )

(defn- remove-gauges [gauge-ns tags]
  (remove-metrics (store :gauges) gauge-ns tags)
  )

(defn- expire-gauges [gauge-ns age]
  (expire-metrics (store :gauges) gauge-ns age)
  )

(defn- read-counter [counter-ns counter-id]
  (read-metric (store :counters) counter-ns counter-id)
  )

(defn- update-counter 
  ([counter-ns counter-id timestamp value]
    (set-metric (store :counters) counter-ns counter-id timestamp value #{})
    (Thread/sleep 250))
  ([counter-ns counter-id timestamp value tags]
    (set-metric (store :counters) counter-ns counter-id timestamp value tags)
    (Thread/sleep 250))
  )

(defn- list-counters [counter-ns tags]
  (list-metrics (store :counters) counter-ns tags)
  )

(defn- remove-counter [counter-ns counter-id]
  (remove-metric (store :counters) counter-ns counter-id)
  )

(defn- remove-counters [counter-ns tags]
  (remove-metrics (store :counters) counter-ns tags)
  )

(defn- expire-counters [counter-ns age]
  (expire-metrics (store :counters) counter-ns age)
  )

(defn- read-counter-history
  ([counter-ns counter-id]
    ((read-history (store :counters) counter-ns counter-id nil) :values)
    )
  ([counter-ns counter-id tags]
    ((read-history (store :counters) counter-ns counter-id tags) :values)
    )
  )

(defn- reset-counter-history [counter-ns counter-id limit]
  (reset-history (store :counters) counter-ns counter-id limit)
  )

(defn- read-timer [timer-ns timer-id]
  (read-metric (store :timers) timer-ns timer-id)
  )

(defn- update-timer 
  ([timer-ns timer-id timestamp value]
    (set-metric (store :timers) timer-ns timer-id timestamp value #{})
    (Thread/sleep 250))
  ([timer-ns timer-id timestamp value tags]
    (set-metric (store :timers) timer-ns timer-id timestamp value tags)
    (Thread/sleep 250))
  )

(defn- list-timers [timer-ns tags]
  (list-metrics (store :timers) timer-ns tags)
  )

(defn- remove-timer [timer-ns timer-id]
  (remove-metric (store :timers) timer-ns timer-id)
  )

(defn- remove-timers [timer-ns tags]
  (remove-metrics (store :timers) timer-ns tags)
  )

(defn- expire-timers [timer-ns age]
  (expire-metrics (store :timers) timer-ns age)
  )

(defn- read-timer-history
  ([timer-ns timer-id]
    ((read-history (store :timers) timer-ns timer-id nil) :values)
    )
  ([timer-ns timer-id tags]
    ((read-history (store :timers) timer-ns timer-id tags) :values)
    )
  )

(defn- reset-timer-history [timer-ns timer-id limit]
  (reset-history (store :timers) timer-ns timer-id limit)
  )

(deftest alert-metrics
  (testing "Null staalerttus"
    (is (nil? (read-alert "alert-metrics" "1")))
    )
  (testing "Initial alert value"
    (update-alert "alert-metrics" "1" "2" "v1")
    (is (not (nil? (read-alert "alert-metrics" "1"))))
    (is (= 2 ((read-alert "alert-metrics" "1") :timestamp)))
    (is (= "v1" ((read-alert "alert-metrics" "1") :alert)))
    (is (= 0 ((read-alert "alert-metrics" "1") :interval-average)))
    (is (= 0 ((read-alert "alert-metrics" "1") :interval-variance)))
    )
  (testing "Updated alert value"
    (update-alert "alert-metrics" "1" "4" "v2")
    (is (not (nil? (read-alert "alert-metrics" "1"))))
    (is (= 4 ((read-alert "alert-metrics" "1") :timestamp)))
    (is (= "v2" ((read-alert "alert-metrics" "1") :alert)))
    (is (= 2 ((read-alert "alert-metrics" "1") :interval-average)))
    (is (= 0 ((read-alert "alert-metrics" "1") :interval-variance)))
    )
  (testing "List and remove alerts"
    (is (= ["1"] (list-alerts "alert-metrics" nil)))
    (remove-alert "alert-metrics" "1")
    (is (= [] (list-alerts "alert-metrics" nil)))
    )
  (testing "List and remove alerts with tags"
    (update-alert "alert-metrics" "2" "3" "v3" #{"tag"})
    (is (= ["2"] (list-alerts "alert-metrics" #{"tag"})))
    (is (= [] (list-alerts "alert-metrics" #{"notag"})))
    (remove-alerts "alert-metrics" #{"tag"})
    (is (= [] (list-alerts "alert-metrics" #{"tag"})))
    )
  (testing "Expire alerts"
    (update-alert "alert-metrics" "3" "4" "v4")
    (Thread/sleep 100)
    (update-alert "alert-metrics" "4" "5" "v5")
    (Thread/sleep 500)
    (expire-alerts "alert-metrics" 1000)
    (is (= ["4"] (list-alerts "alert-metrics" nil)))
    )
  )

(deftest alert-history
  (testing "Reset alert history"
    (reset-alert-history "alert-history" "1" 2)
    )
  (testing "Alert history under limit"
    (update-alert "alert-history" "1" "1" "v1")
    (update-alert "alert-history" "1" "2" "v2")
    (is (= 1 ((first (read-alert-history "alert-history" "1")) :timestamp)))
    (is (= 2 ((second (read-alert-history "alert-history" "1")) :timestamp)))
    )
  (testing "Alert history over limit"
    (update-alert "alert-history" "1" "3" "v3")
    (is (= 2 ((first (read-alert-history "alert-history" "1")) :timestamp)))
    (is (= 3 ((second (read-alert-history "alert-history" "1")) :timestamp)))
    )
  )

(deftest alert-history-with-tags
  (testing "Alert history with tags"
    (update-alert "alert-history-with-tags" "1" "1" "v1" #{"tag1", "tag2"})
    (update-alert "alert-history-with-tags" "1" "2" "v2" #{"tag3"})
    (is (= 1 (count (read-alert-history "alert-history-with-tags" "1" #{"tag1", "tag2"}))))
    (is (= 1 ((first (read-alert-history "alert-history-with-tags" "1" #{"tag1", "tag2"})) :timestamp)))
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
    (Thread/sleep 100)
    (update-gauge "gauge-metrics" "4" "1" "1")
    (Thread/sleep 500)
    (expire-gauges "gauge-metrics" 1000)
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
    (Thread/sleep 100)
    (update-counter "counter-metrics" "4" "1" "1")
    (Thread/sleep 500)
    (expire-counters "counter-metrics" 1000)
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
    (Thread/sleep 100)
    (update-timer "timer-metrics" "4" "1" "start")
    (Thread/sleep 500)
    (expire-timers "timer-metrics" 1000)
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