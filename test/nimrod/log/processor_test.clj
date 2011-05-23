(ns nimrod.log.processor-test
 (:use
   [clojure.test]
   [nimrod.core.metrics]
   [nimrod.log.processor]
   )
 )

(defonce test-metric (atom nil))
(defonce test-metrics {
                       :metric (reify Metric
                                 (set-metric [this metric-ns metric-id timestamp value] 
                                   (reset! test-metric {:log metric-ns :name metric-id :timestamp timestamp :value value})
                                   )
                                 (read-metric [this metric-ns metric-id]
                                   @test-metric
                                   )
                                 )
                       })

(deftest process-log-line
  (testing "Process correct log line"
    (process "log" "[nimrod][1][metric][name][value]" test-metrics)
    (is (= "log" (@test-metric :log)))
    (is (= "1" (@test-metric :timestamp)))
    (is (= "name" (@test-metric :name)))
    (is (= "value" (@test-metric :value)))
    (reset! test-metric nil)
    )
  (testing "No log line processing due to missing prefix"
    (process "log" "[1][metric][name][value]" test-metrics)
    (is (nil? @test-metric))
    (reset! test-metric nil)
    )
  (testing "No log line processing due to bad timestamp"
    (process "log" "[nimrod][bad timestamp][metric][name][value]" test-metrics)
    (is (nil? @test-metric))
    (reset! test-metric nil)
    )
  (testing "No log line processing due to bad metric"
    (process "log" "[nimrod][1][bad metric][name][value]" test-metrics)
    (is (nil? @test-metric))
    (reset! test-metric nil)
    )
  )
