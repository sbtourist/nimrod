(ns nimrod.core.store-test
 (:use
   [clojure.test]
   [nimrod.core.store]))

(defn set-and-read-metric [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" metric-1 {:value 1 :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (testing "Current metric value"
      (is (= metric-1 (read-metric store metric-ns metric-type metric-id))))))

(defn set-and-remove-metric [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" metric-1 {:value 1 :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (remove-metric store metric-ns metric-type metric-id)
    (testing "Current metric is nil"
      (is (nil? (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history is nil"
      (is (nil? (read-history store metric-ns metric-type metric-id #{} nil nil))))))

(defn set-and-read-metric-history-with-current-value-only [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" metric-1 {:value 1 :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (testing "Current metric value"
      (is (= metric-1 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= {:values [metric-1] :size 1 :limit default-limit} (read-history store metric-ns metric-type metric-id #{} nil nil))))))

(defn set-and-read-metric-history-with-old-values-too [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= {:values [metric-3 metric-2 metric-1] :size 3 :limit default-limit} (read-history store metric-ns metric-type metric-id #{} nil nil))))))

(defn set-and-read-metric-history-by-age [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp (+ (System/currentTimeMillis) 1000)}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (Thread/sleep 500)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= {:values [metric-3] :size 1 :limit default-limit} (read-history store metric-ns metric-type metric-id #{} 1000 nil))))))

(defn set-and-read-metric-history-by-tags [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1 :tags #{"t"}}
        metric-2 {:value 2 :timestamp 2 :tags #{"t1"}}
        metric-3 {:value 3 :timestamp 3 :tags #{"t1"}}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= {:values [metric-1] :size 1 :limit default-limit} (read-history store metric-ns metric-type metric-id #{"t"} nil nil))))))

(defn set-and-read-metric-history-with-limit [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (testing "Metric history values with limit"
      (is (= {:values [metric-3 metric-2] :size 2 :limit 2} (read-history store metric-ns metric-type metric-id #{} nil 2))))))

(defn set-and-remove-metric-history-completely [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (remove-history store metric-ns metric-type metric-id 0)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history is nil"
      (is (nil? (read-history store metric-ns metric-type metric-id #{} nil nil))))))

(defn set-and-remove-metric-history-by-id-and-age [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp (+ (System/currentTimeMillis) 1000)}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (Thread/sleep 500)
    (remove-history store metric-ns metric-type metric-id 1000)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (is (= {:values [metric-3] :size 1 :limit default-limit} (read-history store metric-ns metric-type metric-id #{} nil nil))))))

(defn set-and-remove-multiple-metrics-history-by-age [store]
  (let [metric-ns "1" metric-type "gauge" metric-id-1 "1" metric-id-2 "2" metric-id-3 "3" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp (+ (System/currentTimeMillis) 1000)}]
    (set-metric store metric-ns metric-type metric-id-1 metric-1 1)
    (set-metric store metric-ns metric-type metric-id-2 metric-2 2)
    (set-metric store metric-ns metric-type metric-id-3 metric-3 3)
    (Thread/sleep 500)
    (remove-history store metric-ns metric-type 1000)
    (testing "Metric history is nil"
      (is (nil? (read-history store metric-ns metric-type metric-id-1 #{} nil nil)))
      (is (nil? (read-history store metric-ns metric-type metric-id-2 #{} nil nil))))
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id-3))))
    (testing "Metric history values"
      (is (= {:values [metric-3] :size 1 :limit default-limit} (read-history store metric-ns metric-type metric-id-3 #{} nil nil))))))

(defn set-and-merge-metric-history [store]
  (let [metric-ns "1" metric-type "gauge" 
        metric-id-1 "1" metric-id-2 "2" metric-id-3 "3"
        metric-1 {:value 1 :timestamp 1 :tags #{"t"}}
        metric-2 {:value 2 :timestamp 2 :tags #{"t"}}
        metric-3 {:value 3 :timestamp 3 :tags #{"t"}}
        metric-3-1 {:value 31 :timestamp 4 :tags #{"t1"}}]
    (set-metric store metric-ns metric-type metric-id-1 metric-1 1)
    (set-metric store metric-ns metric-type metric-id-2 metric-2 2)
    (set-metric store metric-ns metric-type metric-id-3 metric-3 3)
    (set-metric store metric-ns metric-type metric-id-3 metric-3-1 31)
    (testing "Merged metric history values"
      (is (= {:values [metric-3] :size 1 :limit 2} (merge-history store metric-ns metric-type #{"t"} nil 2))))))

(defn set-and-aggregate-metric-history [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (testing "Metric history aggregation"
      (is (= {:cardinality 3 :percentiles {:50th metric-2}} (aggregate-history store metric-ns metric-type metric-id nil nil {:percentiles [50]}))))))

(defn set-and-aggregate-metric-history-with-time-interval [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (testing "Metric history aggregation"
      (is (= {:cardinality 2 :percentiles {:50th metric-1}} (aggregate-history store metric-ns metric-type metric-id 1 2 {:percentiles [50]}))))))

(defn list-metrics-by-type [store]
  (let [metric-ns "1" metric-type "gauge" 
        metric-id-1 "1" metric-1 {:value 1 :timestamp 1}
        metric-id-2 "2" metric-2 {:value 2 :timestamp 2}]
    (set-metric store metric-ns metric-type metric-id-1 metric-1 1)
    (set-metric store metric-ns metric-type metric-id-2 metric-2 2)
    (is (= ["1" "2"] (list-metrics store metric-ns metric-type)))))

(defn list-types-with-metrics [store]
  (let [metric-ns-1 "1" metric-ns-2 "2" metric-type-1 "counter" metric-type-2 "gauge"
        metric-id-1 "1" metric-1 {:value 1 :timestamp 1}
        metric-id-2 "2" metric-2 {:value 2 :timestamp 2}]
    (set-metric store metric-ns-1 metric-type-1 metric-id-1 metric-1 1)
    (set-metric store metric-ns-2 metric-type-2 metric-id-2 metric-2 2)
    (is (= ["counter"] (list-types store metric-ns-1)))
    (is (= ["gauge"] (list-types store metric-ns-2)))))

(defn read-non-existent-metric [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1"]
    (is (nil? (read-metric store metric-ns metric-type metric-id)))))

(defn read-non-existent-history [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1"]
    (is (nil? (read-history store metric-ns metric-type metric-id #{"t"} nil nil)))))

(defn list-non-existent-metrics [store]
  (let [metric-ns "1" metric-type "gauge"]
    (is (nil? (list-metrics store metric-ns metric-type)))))

(defn list-types-after-removal [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" metric {:value 1 :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric 1)
    (remove-metric store metric-ns metric-type metric-id)
    (is (= [] (list-types store metric-ns)))))

(defn post-init [store]
  (let [metric-ns-1 "1" metric-ns-2 "2" metric-type-1 "counter" metric-type-2 "gauge" metric-id-1 "1" metric-id-2 "2"
        metric-1-1 {:value 1 :timestamp 1} metric-1-2 {:value 12 :timestamp 2}
        metric-2-1 {:value 2 :timestamp 1} metric-2-2 {:value 22 :timestamp 2}]
    (set-metric store metric-ns-1 metric-type-1 metric-id-1 metric-1-1 1)
    (set-metric store metric-ns-1 metric-type-1 metric-id-1 metric-1-2 12)
    (set-metric store metric-ns-2 metric-type-2 metric-id-2 metric-2-1 2)
    (set-metric store metric-ns-2 metric-type-2 metric-id-2 metric-2-2 22)
    (init store)
    (is (= metric-1-2 (read-metric store metric-ns-1 metric-type-1 metric-id-1)))
    (is (= metric-2-2 (read-metric store metric-ns-2 metric-type-2 metric-id-2)))))

(deftest memory-store-test 
  (set-and-read-metric (new-memory-store))
  (set-and-remove-metric (new-memory-store))
  (set-and-read-metric-history-with-current-value-only (new-memory-store))
  (set-and-read-metric-history-with-old-values-too (new-memory-store))
  (set-and-read-metric-history-by-age (new-memory-store))
  (set-and-read-metric-history-by-tags (new-memory-store))
  (set-and-read-metric-history-with-limit (new-memory-store))
  (set-and-remove-metric-history-completely (new-memory-store))
  (set-and-remove-metric-history-by-id-and-age (new-memory-store))
  (set-and-remove-multiple-metrics-history-by-age (new-memory-store))
  (read-non-existent-metric (new-memory-store))
  (read-non-existent-history (new-memory-store))
  (list-non-existent-metrics (new-memory-store))
  (list-metrics-by-type (new-memory-store))
  (list-types-with-metrics (new-memory-store))
  (list-types-after-removal (new-memory-store))
  (post-init (new-memory-store)))

(deftest disk-store-test 
  (set-and-read-metric (new-disk-store (java.io.File/createTempFile "test" "1")))
  (set-and-remove-metric (new-disk-store (java.io.File/createTempFile "test" "2")))
  (set-and-read-metric-history-with-current-value-only (new-disk-store (java.io.File/createTempFile "test" "3")))
  (set-and-read-metric-history-with-old-values-too (new-disk-store (java.io.File/createTempFile "test" "4")))
  (set-and-read-metric-history-by-age (new-disk-store (java.io.File/createTempFile "test" "5")))
  (set-and-read-metric-history-by-tags (new-disk-store (java.io.File/createTempFile "test" "6")))
  (set-and-read-metric-history-with-limit (new-disk-store (java.io.File/createTempFile "test" "7")))
  (set-and-remove-metric-history-completely (new-disk-store (java.io.File/createTempFile "test" "8")))
  (set-and-remove-metric-history-by-id-and-age (new-disk-store (java.io.File/createTempFile "test" "9")))
  (set-and-remove-multiple-metrics-history-by-age (new-disk-store (java.io.File/createTempFile "test" "10")))
  (set-and-merge-metric-history (new-disk-store (java.io.File/createTempFile "test" "11")))
  (set-and-aggregate-metric-history (new-disk-store (java.io.File/createTempFile "test" "12")))
  (set-and-aggregate-metric-history-with-time-interval (new-disk-store (java.io.File/createTempFile "test" "13")))
  (read-non-existent-metric (new-disk-store (java.io.File/createTempFile "test" "14")))
  (read-non-existent-history (new-disk-store (java.io.File/createTempFile "test" "15")))
  (list-non-existent-metrics (new-disk-store (java.io.File/createTempFile "test" "16")))
  (list-metrics-by-type (new-disk-store (java.io.File/createTempFile "test" "17")))
  (list-types-with-metrics (new-disk-store (java.io.File/createTempFile "test" "18")))
  (list-types-after-removal (new-disk-store (java.io.File/createTempFile "test" "19")))
  (post-init (new-disk-store (java.io.File/createTempFile "test" "20"))))