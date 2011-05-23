(ns nimrod.log.processor-test
 (:use
   [clojure.test]
   [nimrod.log.processor]
   )
 )

(deftest process-log-line
  (testing "Process correct log line"
    (let [processed (atom nil) metric-fn #(reset! processed {:log %1 :name %2 :timestamp %3 :value %4}) metrics {"metric" metric-fn}]
      (process "log" "[nimrod][1][metric][name][value]" metrics)
      (is (= "log" (@processed :log)))
      (is (= "1" (@processed :timestamp)))
      (is (= "name" (@processed :name)))
      (is (= "value" (@processed :value)))
      )
    )
  (testing "No log line processing due to missing prefix"
    (let [processed (atom nil) metric-fn #(reset! processed "processed") metrics {"metric" metric-fn}]
      (process "log" "[1][metric][name][value]" metrics)
      (is (nil? @processed))
      )
    )
  (testing "No log line processing due to bad timestamp"
    (let [processed (atom nil) metric-fn #(reset! processed "processed") metrics {"metric" metric-fn}]
      (process "log" "[nimrod][bad timestamp][metric][name][value]" metrics)
      (is (nil? @processed))
      )
    )
  (testing "No log line processing due to bad metric"
    (let [processed (atom nil) metric-fn #(reset! processed "processed") metrics {"metric" metric-fn}]
      (process "log" "[nimrod][1][bad metric][name][value]" metrics)
      (is (nil? @processed))
      )
    )
  )