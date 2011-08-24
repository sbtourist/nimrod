(ns nimrod.log.processor-test
 (:use
   [clojure.test]
   [nimrod.core.metrics]
   [nimrod.log.processor]
   )
 )

(defonce test-metric (atom nil))
(defonce test-store {
                       :metric (reify Metrics
                                 (set-metric [this metric-ns metric-id timestamp value tags]
                                   (reset! test-metric {:log metric-ns :name metric-id :timestamp timestamp :value value :tags tags})
                                   )
                                 )
                       })

(deftest process-log-line
  (testing "Process full log line"
    (binding [store test-store]
      (process "log" "[nimrod][1][metric][name][value][tag1,tag2]")
      (is (= "log" (@test-metric :log)))
      (is (= "1" (@test-metric :timestamp)))
      (is (= "name" (@test-metric :name)))
      (is (= "value" (@test-metric :value)))
      (is (= #{"tag1" "tag2"} (@test-metric :tags)))
      (reset! test-metric nil)
      )
    )
  (testing "Process full log line with interleaved text"
    (binding [store test-store]
      (process "log" "this[nimrod]is[1]an[metric]interleaved[name]text[value]string[tag1,tag2]!")
      (is (= "log" (@test-metric :log)))
      (is (= "1" (@test-metric :timestamp)))
      (is (= "name" (@test-metric :name)))
      (is (= "value" (@test-metric :value)))
      (is (= #{"tag1" "tag2"} (@test-metric :tags)))
      (reset! test-metric nil)
      )
    )
  (testing "Process log line with no tags"
    (binding [store test-store]
      (process "log" "[nimrod][1][metric][name][value]")
      (is (= "log" (@test-metric :log)))
      (is (= "1" (@test-metric :timestamp)))
      (is (= "name" (@test-metric :name)))
      (is (= "value" (@test-metric :value)))
      (is (= #{} (@test-metric :tags)))
      (reset! test-metric nil)
      )
    )
  (testing "No log line processing due to missing prefix"
    (binding [store test-store]
      (process "log" "[1][metric][name][value]")
      (is (nil? @test-metric))
      (reset! test-metric nil)
      )
    )
  (testing "No log line processing due to bad timestamp"
    (binding [store test-store]
      (process "log" "[nimrod][bad timestamp][metric][name][value]")
      (is (nil? @test-metric))
      (reset! test-metric nil)
      )
    )
  (testing "No log line processing due to bad metric"
    (binding [store test-store]
      (process "log" "[nimrod][1][bad metric][name][value]")
      (is (nil? @test-metric))
      (reset! test-metric nil)
      )
    )
  )
