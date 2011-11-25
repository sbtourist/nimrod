(ns nimrod.core.store-test
 (:use
   [clojure.test]
   [nimrod.core.store]))

(defn set-and-read-metric [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1" metric-1 {:value "v1" :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (testing "Current metric value"
      (is (= metric-1 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history value"
      (is (= [metric-1] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))

(defn set-and-read-multiple-metrics [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1" 
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

(defn set-and-read-multiple-metrics-with-current-value-only [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1" metric-1 {:value "v1" :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (testing "Current metric value"
      (is (= metric-1 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= [metric-1] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))

(defn set-and-read-multiple-metrics-by-age [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1" 
        metric-1 {:value "v1" :timestamp 1}
        metric-2 {:value "v2" :timestamp 2}
        metric-3 {:value "v3" :timestamp (+ (System/currentTimeMillis) 1000)}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (set-metric store metric-ns metric-type metric-id metric-2)
    (set-metric store metric-ns metric-type metric-id metric-3)
    (Thread/sleep 500)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= [metric-3] (read-metrics store metric-ns metric-type metric-id 1000 #{}))))))

(defn set-and-read-multiple-metrics-by-tags [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1" 
        metric-1 {:value "v1" :timestamp 1 :tags #{"t"}}
        metric-2 {:value "v2" :timestamp 2 :tags #{"t1"}}
        metric-3 {:value "v3" :timestamp 3 :tags #{"t1"}}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (set-metric store metric-ns metric-type metric-id metric-2)
    (set-metric store metric-ns metric-type metric-id metric-3)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= [metric-1] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{"t"}))))))

(defn set-and-remove-metric [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1" metric-1 {:value "v1" :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (remove-metric store metric-ns metric-type metric-id)
    (testing "Current metric is nil"
      (is (nil? (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history is nil"
      (is (= nil (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))

(defn set-and-remove-multiple-metrics [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1" 
        metric-1 {:value "v1" :timestamp 1}
        metric-2 {:value "v2" :timestamp 2}
        metric-3 {:value "v3" :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (set-metric store metric-ns metric-type metric-id metric-2)
    (set-metric store metric-ns metric-type metric-id metric-3)
    (remove-metrics store metric-ns metric-type metric-id 0)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history is nil"
      (is (= nil (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))

(defn set-and-remove-multiple-metrics-by-age [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1" 
        metric-1 {:value "v1" :timestamp 1}
        metric-2 {:value "v2" :timestamp 2}
        metric-3 {:value "v3" :timestamp (+ (System/currentTimeMillis) 1000)}]
    (set-metric store metric-ns metric-type metric-id metric-1)
    (set-metric store metric-ns metric-type metric-id metric-2)
    (set-metric store metric-ns metric-type metric-id metric-3)
    (Thread/sleep 500)
    (remove-metrics store metric-ns metric-type metric-id 1000)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= [metric-3] (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{}))))))


(defn list-multiple-metrics [store]
  (let [metric-ns "1" metric-type "alert" 
        metric-id-1 "1" metric-1 {:value "v1" :timestamp 1}
        metric-id-2 "2" metric-2 {:value "v2" :timestamp 2}]
    (set-metric store metric-ns metric-type metric-id-1 metric-1)
    (set-metric store metric-ns metric-type metric-id-2 metric-2)
    (is (= ["1" "2"] (list-metrics store metric-ns metric-type)))))

(defn read-non-existent-metric [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1"]
    (is (nil? (read-metric store metric-ns metric-type metric-id)))))

(defn read-non-existent-metrics [store]
  (let [metric-ns "1" metric-type "alert" metric-id "1"]
    (is (nil? (read-metrics store metric-ns metric-type metric-id Long/MAX_VALUE #{"t"})))))

(defn list-non-existent-metrics [store]
  (let [metric-ns "1" metric-type "alert"]
    (is (nil? (list-metrics store metric-ns metric-type)))))

(deftest memory-store-test 
  (set-and-read-metric (new-memory-store))
  (set-and-read-multiple-metrics (new-memory-store))
  (set-and-read-multiple-metrics-with-current-value-only (new-memory-store))
  (set-and-read-multiple-metrics-by-age (new-memory-store))
  (set-and-read-multiple-metrics-by-tags (new-memory-store))
  (set-and-remove-metric (new-memory-store))
  (set-and-remove-multiple-metrics (new-memory-store))
  (set-and-remove-multiple-metrics-by-age (new-memory-store))
  (list-multiple-metrics (new-memory-store))
  (read-non-existent-metric (new-memory-store))
  (read-non-existent-metrics (new-memory-store))
  (list-non-existent-metrics (new-memory-store)))

(deftest disk-store-test 
  (set-and-read-metric (new-disk-store (java.io.File/createTempFile "test" "1")))
  (set-and-read-multiple-metrics (new-disk-store (java.io.File/createTempFile "test" "2")))
  (set-and-read-multiple-metrics-with-current-value-only (new-disk-store (java.io.File/createTempFile "test" "3")))
  (set-and-read-multiple-metrics-by-age (new-disk-store (java.io.File/createTempFile "test" "4")))
  (set-and-read-multiple-metrics-by-tags (new-disk-store (java.io.File/createTempFile "test" "5")))
  (set-and-remove-metric (new-disk-store (java.io.File/createTempFile "test" "6")))
  (set-and-remove-multiple-metrics (new-disk-store (java.io.File/createTempFile "test" "7")))
  (set-and-remove-multiple-metrics-by-age (new-disk-store (java.io.File/createTempFile "test" "8")))
  (list-multiple-metrics (new-disk-store (java.io.File/createTempFile "test" "9")))
  (read-non-existent-metric (new-disk-store (java.io.File/createTempFile "test" "10")))
  (read-non-existent-metrics (new-disk-store (java.io.File/createTempFile "test" "11")))
  (list-non-existent-metrics (new-disk-store (java.io.File/createTempFile "test" "12"))))