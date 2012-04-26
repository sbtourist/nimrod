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
    (testing "Metric history is *not* nil (as requires a different operation)"
      (let [history (read-history store metric-ns metric-type metric-id #{} nil 0 Long/MAX_VALUE)]
        (is (= [metric-1] (history :values)))))))

(defn set-and-read-metric-history-with-current-value-only [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" metric-1 {:value 1 :timestamp 1}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (testing "Current metric value"
      (is (= metric-1 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (let [history (read-history store metric-ns metric-type metric-id #{} nil 0 Long/MAX_VALUE)]
        (is (= [metric-1] (history :values)))))))

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
      (let [history (read-history store metric-ns metric-type metric-id #{} nil 0 Long/MAX_VALUE)]
        (is (= [metric-3 metric-2 metric-1] (history :values)))))))

(defn set-and-read-metric-history-by-age [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp (+ (System/currentTimeMillis) 1000)}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (Thread/sleep 1000)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (let [history (read-history store metric-ns metric-type metric-id #{} 2000 nil nil)]
        (is (= [metric-3] (history :values)))))))

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
      (let [history (read-history store metric-ns metric-type metric-id #{"t"} nil 0 Long/MAX_VALUE)]
        (is (= [metric-1] (history :values)))))))

(defn set-and-read-metric-history-with-sampling [store]
  (let [metric-ns-1 "1" metric-type-1 "gauge" metric-id-1 "test" 
        metric-1-1 {:value 1 :timestamp 1}
        metric-1-2 {:value 2 :timestamp 2}
        metric-1-3 {:value 3 :timestamp 3}
        metric-1-4 {:value 4 :timestamp 4}
        metric-ns-2 "2" metric-type-2 "gauge" metric-id-2 "test" 
        metric-2-1 {:value 1 :timestamp 1}
        metric-2-2 {:value 2 :timestamp 2}
        metric-2-3 {:value 3 :timestamp 3}
        metric-2-4 {:value 4 :timestamp 4}
        metric-ns-3 "3" metric-type-3 "gauge" metric-id-3 "test" 
        metric-3-1 {:value 1 :timestamp 1}
        metric-3-2 {:value 2 :timestamp 2}
        metric-3-3 {:value 3 :timestamp 3}
        metric-3-4 {:value 4 :timestamp 4}]
    (set-metric store metric-ns-1 metric-type-1 metric-id-1 metric-1-1 1)
    (set-metric store metric-ns-1 metric-type-1 metric-id-1 metric-1-2 2)
    (set-metric store metric-ns-1 metric-type-1 metric-id-1 metric-1-3 3)
    (set-metric store metric-ns-1 metric-type-1 metric-id-1 metric-1-4 4)
    (set-metric store metric-ns-2 metric-type-2 metric-id-2 metric-2-1 1)
    (set-metric store metric-ns-2 metric-type-2 metric-id-2 metric-2-2 2)
    (set-metric store metric-ns-2 metric-type-2 metric-id-2 metric-2-3 3)
    (set-metric store metric-ns-2 metric-type-2 metric-id-2 metric-2-4 4)
    (set-metric store metric-ns-3 metric-type-3 metric-id-3 metric-3-1 1)
    (set-metric store metric-ns-3 metric-type-3 metric-id-3 metric-3-2 2)
    (set-metric store metric-ns-3 metric-type-3 metric-id-3 metric-3-3 3)
    (set-metric store metric-ns-3 metric-type-3 metric-id-3 metric-3-4 4)
    (testing "Metric history values with sampling configured on metrics namespace"
      (let [history (read-history store metric-ns-1 metric-type-1 metric-id-1 #{} nil 0 Long/MAX_VALUE)]
        (is (= 3 (count (history :values))))))
    (testing "Metric history values with sampling configured on metrics namespace and type"
      (let [history (read-history store metric-ns-2 metric-type-2 metric-id-2 #{} nil 0 Long/MAX_VALUE)]
        (is (= 3 (count (history :values))))))
    (testing "Metric history values with sampling configured on metrics namespace, type and id"
      (let [history (read-history store metric-ns-3 metric-type-3 metric-id-3 #{} nil 0 Long/MAX_VALUE)]
        (is (= 3 (count (history :values))))))))

(defn set-and-remove-metric-history [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (remove-history store metric-ns metric-type metric-id nil 0 Long/MAX_VALUE)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Current metric value is not nil"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history keeps current value"
      (let [history (read-history store metric-ns metric-type metric-id #{} nil 0 Long/MAX_VALUE)]
        (is (= [metric-3] (history :values)))))))

(defn set-and-remove-metric-history-by-age [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp (+ (System/currentTimeMillis) 1000)}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (Thread/sleep 500)
    (remove-history store metric-ns metric-type metric-id 1000 nil nil)
    (testing "Current metric value"
      (is (= metric-3 (read-metric store metric-ns metric-type metric-id))))
    (testing "Metric history values"
      (let [history (read-history store metric-ns metric-type metric-id #{} nil 0 Long/MAX_VALUE)]
        (is (= [metric-3] (history :values)))))))

(defn set-and-aggregate-metric-history [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (testing "Metric history aggregation"
      (let [aggregate (aggregate-history store metric-ns metric-type metric-id Long/MAX_VALUE nil nil {:percentiles [50]})]
        (is (= 3 (aggregate :count)))
        (is (= 2.0 (aggregate :median)))
        (is (= {:50th metric-2} (aggregate :percentiles)))))))

(defn set-and-aggregate-metric-history-by-time-interval [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp 3}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (testing "Metric history aggregation"
      (let [aggregate (aggregate-history store metric-ns metric-type metric-id nil 1 2 {:percentiles [50]})]
        (is (= 2 (aggregate :count)))
        (is (= 1.5 (aggregate :median)))
        (is (= {:50th metric-2} (aggregate :percentiles)))))))

(defn set-and-aggregate-metric-history-by-age [store]
  (let [metric-ns "1" metric-type "gauge" metric-id "1" 
        metric-1 {:value 1 :timestamp 1}
        metric-2 {:value 2 :timestamp 2}
        metric-3 {:value 3 :timestamp (System/currentTimeMillis)}]
    (set-metric store metric-ns metric-type metric-id metric-1 1)
    (set-metric store metric-ns metric-type metric-id metric-2 2)
    (set-metric store metric-ns metric-type metric-id metric-3 3)
    (testing "Metric history aggregation"
      (let [aggregate (aggregate-history store metric-ns metric-type metric-id 1000 nil nil {:percentiles [50]})]
        (is (= 1 (aggregate :count)))
        (is (= 3.0 (aggregate :median)))
        (is (= {:50th metric-3} (aggregate :percentiles)))))))

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
    (is (nil? (read-history store metric-ns metric-type metric-id #{"t"} nil 0 Long/MAX_VALUE)))))

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

(deftest disk-store-test 
  (set-and-read-metric (new-disk-store (java.io.File/createTempFile "test" "1")))
  (set-and-remove-metric (new-disk-store (java.io.File/createTempFile "test" "2")))
  (set-and-read-metric-history-with-current-value-only (new-disk-store (java.io.File/createTempFile "test" "3")))
  (set-and-read-metric-history-with-old-values-too (new-disk-store (java.io.File/createTempFile "test" "4")))
  (set-and-read-metric-history-by-age (new-disk-store (java.io.File/createTempFile "test" "5")))
  (set-and-read-metric-history-by-tags (new-disk-store (java.io.File/createTempFile "test" "6")))
  (set-and-read-metric-history-with-sampling 
    (new-disk-store (java.io.File/createTempFile "test" "7") {} 
      {"1.factor" 2 "1.frequency" 2 "2.gauge.factor" 2 "2.gauge.frequency" 2 "3.gauge.test.factor" 2 "3.gauge.test.frequency" 2}))
  (set-and-remove-metric-history (new-disk-store (java.io.File/createTempFile "test" "8")))
  (set-and-remove-metric-history-by-age (new-disk-store (java.io.File/createTempFile "test" "9")))
  (set-and-aggregate-metric-history (new-disk-store (java.io.File/createTempFile "test" "10")))
  (set-and-aggregate-metric-history-by-time-interval (new-disk-store (java.io.File/createTempFile "test" "11")))
  (set-and-aggregate-metric-history-by-age (new-disk-store (java.io.File/createTempFile "test" "12")))
  (read-non-existent-metric (new-disk-store (java.io.File/createTempFile "test" "13")))
  (read-non-existent-history (new-disk-store (java.io.File/createTempFile "test" "14")))
  (list-non-existent-metrics (new-disk-store (java.io.File/createTempFile "test" "15")))
  (list-metrics-by-type (new-disk-store (java.io.File/createTempFile "test" "16")))
  (list-types-with-metrics (new-disk-store (java.io.File/createTempFile "test" "17")))
  (list-types-after-removal (new-disk-store (java.io.File/createTempFile "test" "18")))
  (post-init (new-disk-store (java.io.File/createTempFile "test" "19"))))