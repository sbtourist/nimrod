(ns nimrod.log.processor-test
 (:use
   [clojure.test]
   [nimrod.core.metrics]
   [nimrod.log.processor]
   )
 )

(defonce test-metric (atom nil))
(defonce test-metrics {
                       :metric (reify MetricProtocol
                                 (set-metric [this metric-ns metric-id timestamp value] 
                                   (reset! test-metric {:log metric-ns :name metric-id :timestamp timestamp :value value})
                                   )
                                 )
                       })

(deftest process-log-line
  (testing "Process correct log line"
    (binding [metric-types test-metrics]
      (process "log" "[nimrod][1][metric][name][value]")
      (is (= "log" (@test-metric :log)))
      (is (= "1" (@test-metric :timestamp)))
      (is (= "name" (@test-metric :name)))
      (is (= "value" (@test-metric :value)))
      (reset! test-metric nil)
      )
    )
  (testing "No log line processing due to missing prefix"
    (binding [metric-types test-metrics]
      (process "log" "[1][metric][name][value]")
      (is (nil? @test-metric))
      (reset! test-metric nil)
      )
    )
  (testing "No log line processing due to bad timestamp"
    (binding [metric-types test-metrics]
      (process "log" "[nimrod][bad timestamp][metric][name][value]")
      (is (nil? @test-metric))
      (reset! test-metric nil)
      )
    )
  (testing "No log line processing due to bad metric"
    (binding [metric-types test-metrics]
      (process "log" "[nimrod][1][bad metric][name][value]")
      (is (nil? @test-metric))
      (reset! test-metric nil)
      )
    )
  )
