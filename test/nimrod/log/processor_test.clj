(ns nimrod.log.processor-test
 (:use
   [clojure.test]
   [nimrod.log.processor]
   )
 )

(deftest process-log-line
  (let [processed (atom nil) metric-fn #(reset! processed {:name %1 :timestamp %2 :value %3}) metrics {"metric" metric-fn}]
    (process "[1][metric][test][value]" metrics)
    (is (= "test" (@processed :name)))
    (is (= "1" (@processed :timestamp)))
    (is (= "value" (@processed :value)))
    )
  )