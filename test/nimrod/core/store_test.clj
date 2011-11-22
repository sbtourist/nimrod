(ns nimrod.core.store-test
 (:use
   [clojure.test]
   [nimrod.core.store]))

(deftest set-and-read-metric
  (let [store (new-memory-store)
        metric-ns "1" metric-type "alert" metric-id "1" metric-1 {:value "v1" :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (testing "Current metric value"
      (is (= metric-1 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history value"
      (is (= [metric-1] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))

(deftest set-and-read-multiple-metrics
  (let [store (new-memory-store)
        metric-ns "1" metric-type "alert" metric-id "1" 
        metric-1 {:value "v1" :timestamp 1}
        metric-2 {:value "v2" :timestamp 2}
        metric-3 {:value "v3" :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (set-metric store metric-ns metric-type metric-id metric-2)
    (set-metric store metric-ns metric-type metric-id metric-3)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= [metric-3 metric-2 metric-1] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))

(deftest set-and-read-multiple-metrics-with-current-value-only
  (let [store (new-memory-store)
        metric-ns "1" metric-type "alert" metric-id "1" metric-1 {:value "v1" :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (testing "Current metric value"
      (is (= metric-1 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= [metric-1] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))

(deftest set-and-read-multiple-metrics-by-age
  (let [store (new-memory-store)
        metric-ns "1" metric-type "alert" metric-id "1" 
        metric-1 {:value "v1" :timestamp (System/currentTimeMillis)}
        metric-2 {:value "v2" :timestamp (System/currentTimeMillis)}
        metric-3 {:value "v3" :timestamp (+ (System/currentTimeMillis) 500)}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (set-metric store metric-ns metric-type metric-id metric-2)
    (set-metric store metric-ns metric-type metric-id metric-3)
    (Thread/sleep 500)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= [metric-3] (read-metrics store metric-ns metric-type metric-id 500 #{}))))))

(deftest set-and-read-multiple-metrics-by-tags
  (let [store (new-memory-store)
        metric-ns "1" metric-type "alert" metric-id "1" 
        metric-1 {:value "v1" :timestamp 1 :tags #{:tag "t"}}
        metric-2 {:value "v2" :timestamp 2 :tags #{:tag "t1"}}
        metric-3 {:value "v3" :timestamp 3 :tags #{:tag "t1"}}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (set-metric store metric-ns metric-type metric-id metric-2)
    (set-metric store metric-ns metric-type metric-id metric-3)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= [metric-1] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{:tag "t"}))))))

(deftest set-and-remove-metric
  (let [store (new-memory-store)
        metric-ns "1" metric-type "alert" metric-id "1" metric-1 {:value "v1" :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (remove-metric store metric-ns metric-type metric-id)
    (testing "Current metric is nil"
      (is (nil? (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history is empty"
      (is (= [] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))

(deftest set-and-remove-multiple-metrics
  (let [store (new-memory-store)
        metric-ns "1" metric-type "alert" metric-id "1" 
        metric-1 {:value "v1" :timestamp 1}
        metric-2 {:value "v2" :timestamp 2}
        metric-3 {:value "v3" :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (set-metric store metric-ns metric-type metric-id metric-2)
    (set-metric store metric-ns metric-type metric-id metric-3)
    (remove-metrics store metric-ns metric-type metric-id 0)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history is empty"
      (is (= [] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))

(deftest set-and-remove-multiple-metrics-by-age
  (let [store (new-memory-store)
        metric-ns "1" metric-type "alert" metric-id "1" 
        metric-1 {:value "v1" :timestamp (System/currentTimeMillis)}
        metric-2 {:value "v2" :timestamp (System/currentTimeMillis)}
        metric-3 {:value "v3" :timestamp (+ (System/currentTimeMillis) 500)}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (set-metric store metric-ns metric-type metric-id metric-2)
    (set-metric store metric-ns metric-type metric-id metric-3)
    (Thread/sleep 500)
    (remove-metrics store metric-ns metric-type metric-id 500)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= [metric-3] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))


(deftest list-multiple-metrics
  (let [store (new-memory-store)
        metric-ns "1" metric-type "alert" 
        metric-id-1 "1" metric-1 {:value "v1" :timestamp 1}
        metric-id-2 "2" metric-2 {:value "v2" :timestamp 2}]
    (set-metric store metric-ns metric-type metric-id-1 metric-1)
    (set-metric store metric-ns metric-type metric-id-2 metric-2)
    (is (= ["1" "2"] (list-metrics store metric-ns metric-type)))))