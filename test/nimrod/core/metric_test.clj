(ns nimrod.core.metric-test
 (:use
   [clojure.test]
   [nimrod.core.metric]
   [nimrod.core.store]))

(defn- update-alert
  ([alert-ns alert-id timestamp value]
    (compute-metric alert alert-ns alert-id timestamp value #{}))
  ([alert-ns alert-id timestamp value tags]
    (compute-metric alert alert-ns alert-id timestamp value tags)))

(defn- read-alert [alert-ns alert-id]
  (read-metric @metrics-store alert-ns (name-of alert) alert-id))

(defn- update-gauge
  ([gauge-ns gauge-id timestamp value]
    (compute-metric gauge gauge-ns gauge-id timestamp value #{}))
  ([gauge-ns gauge-id timestamp value tags]
    (compute-metric gauge gauge-ns gauge-id timestamp value tags)))

(defn- read-gauge [gauge-ns gauge-id]
  (read-metric @metrics-store gauge-ns (name-of gauge) gauge-id))

(defn- update-counter 
  ([counter-ns counter-id timestamp value]
    (compute-metric counter counter-ns counter-id timestamp value #{}))
  ([counter-ns counter-id timestamp value tags]
    (compute-metric counter counter-ns counter-id timestamp value tags)))

(defn- read-counter [counter-ns counter-id]
  (read-metric @metrics-store counter-ns (name-of counter) counter-id))

(defn- update-timer 
  ([timer-ns timer-id timestamp value]
    (compute-metric timer timer-ns timer-id timestamp value #{}))
  ([timer-ns timer-id timestamp value tags]
    (compute-metric timer timer-ns timer-id timestamp value tags)))

(defn- read-timer [timer-ns timer-id]
  (read-metric @metrics-store timer-ns (name-of timer) timer-id))

(deftest alert-metrics
  (setup-metric-store (new-disk-store (java.io.File/createTempFile "test" "alert-metrics")))
  (testing "Null alert"
    (is (nil? (read-alert "alert-metrics" "1"))))
  (testing "Initial alert value"
    (update-alert "alert-metrics" "1" "2" "v1")
    (is (not (nil? (read-alert "alert-metrics" "1"))))
    (is (= 2 ((read-alert "alert-metrics" "1") :timestamp)))
    (is (= "v1" ((read-alert "alert-metrics" "1") :alert))))
  (testing "Updated alert value"
    (update-alert "alert-metrics" "1" "4" "v2")
    (is (not (nil? (read-alert "alert-metrics" "1"))))
    (is (= 4 ((read-alert "alert-metrics" "1") :timestamp)))
    (is (= "v2" ((read-alert "alert-metrics" "1") :alert)))))

(deftest gauge-metrics
  (setup-metric-store (new-disk-store (java.io.File/createTempFile "test" "gauge-metrics")))
  (testing "Null gauge"
    (is (nil? (read-gauge "gauge-metrics" "1"))))
  (testing "Initial gauge values"
    (update-gauge "gauge-metrics" "1" "2" "4")
    (is (not (nil? (read-gauge "gauge-metrics" "1"))))
    (is (= 2 ((read-gauge "gauge-metrics" "1") :timestamp)))
    (is (= 4 ((read-gauge "gauge-metrics" "1") :gauge))))
  (testing "Updated gauge values"
    (update-gauge "gauge-metrics" "1" "4" "6")
    (is (not (nil? (read-gauge "gauge-metrics" "1"))))
    (is (= 4 ((read-gauge "gauge-metrics" "1") :timestamp)))
    (is (= 6 ((read-gauge "gauge-metrics" "1") :gauge)))))

(deftest counter-metrics
  (setup-metric-store (new-disk-store (java.io.File/createTempFile "test" "counter-metrics")))
  (testing "Null counter"
    (is (nil? (read-counter "counter-metrics" "1"))))
  (testing "Initial counter values"
    (update-counter "counter-metrics" "1" "2" "4")
    (is (not (nil? (read-counter "counter-metrics" "1"))))
    (is (= 2 ((read-counter "counter-metrics" "1") :timestamp)))
    (is (= 4 ((read-counter "counter-metrics" "1") :counter)))
    (is (= 4 ((read-counter "counter-metrics" "1") :latest-increment))))
  (testing "Updated counter values"
    (update-counter "counter-metrics" "1" "4" "6")
    (is (not (nil? (read-counter "counter-metrics" "1"))))
    (is (= 4 ((read-counter "counter-metrics" "1") :timestamp)))
    (is (= 10 ((read-counter "counter-metrics" "1") :counter)))
    (is (= 6 ((read-counter "counter-metrics" "1") :latest-increment)))))

(deftest timer-metrics
  (setup-metric-store (new-disk-store (java.io.File/createTempFile "test" "timer-metrics")))
  (testing "Null timer"
    (is (nil? (read-timer "timer-metrics" "1"))))
  (testing "Start timer"
    (update-timer "timer-metrics" "1" "2" "start")
    (is (not (nil? (read-timer "timer-metrics" "1"))))
    (is (= 2 ((read-timer "timer-metrics" "1") :start)))
    (is (= 0 ((read-timer "timer-metrics" "1") :end)))
    (is (= 0 ((read-timer "timer-metrics" "1") :elapsed-time))))
  (testing "Stop timer"
    (update-timer "timer-metrics" "1" "4" "stop")
    (is (not (nil? (read-timer "timer-metrics" "1"))))
    (is (= 2 ((read-timer "timer-metrics" "1") :start)))
    (is (= 4 ((read-timer "timer-metrics" "1") :end)))
    (is (= 2 ((read-timer "timer-metrics" "1") :elapsed-time))))
  (testing "Restart timer"
    (update-timer "timer-metrics" "1" "6" "start")
    (is (not (nil? (read-timer "timer-metrics" "1"))))
    (is (= 6 ((read-timer "timer-metrics" "1") :start)))
    (is (= 0 ((read-timer "timer-metrics" "1") :end)))
    (is (= 0 ((read-timer "timer-metrics" "1") :elapsed-time)))
    (update-timer "timer-metrics" "1" "10" "stop")
    (is (not (nil? (read-timer "timer-metrics" "1"))))
    (is (= 6 ((read-timer "timer-metrics" "1") :start)))
    (is (= 10 ((read-timer "timer-metrics" "1") :end)))
    (is (= 4 ((read-timer "timer-metrics" "1") :elapsed-time)))))
