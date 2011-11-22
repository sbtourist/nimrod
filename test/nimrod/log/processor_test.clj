(ns nimrod.log.processor-test
 (:use
   [clojure.test]
   [nimrod.core.metric]
   [nimrod.log.processor]))

(defonce test-metric (atom nil))
(defn test-compute-metric [type metric-ns metric-id timestamp value tags]
  (reset! test-metric {:type type :log metric-ns :name metric-id :timestamp timestamp :value value :tags tags}))

(deftest process-log-line
  (testing "Process full log line"
    (with-redefs [nimrod.core.metric/compute-metric test-compute-metric]
      (process "log" "[nimrod][1][alert][name][value][tag1,tag2]")
      (is (= alert (@test-metric :type)))
      (is (= "log" (@test-metric :log)))
      (is (= "1" (@test-metric :timestamp)))
      (is (= "name" (@test-metric :name)))
      (is (= "value" (@test-metric :value)))
      (is (= #{"tag1" "tag2"} (@test-metric :tags)))
      (reset! test-metric nil)))
  (testing "Process full log line with interleaved text"
    (with-redefs [nimrod.core.metric/compute-metric test-compute-metric]
      (process "log" "this[nimrod]is[1]an[alert]interleaved[name]text[value]string[tag1,tag2]!")
      (is (= alert (@test-metric :type)))
      (is (= "log" (@test-metric :log)))
      (is (= "1" (@test-metric :timestamp)))
      (is (= "name" (@test-metric :name)))
      (is (= "value" (@test-metric :value)))
      (is (= #{"tag1" "tag2"} (@test-metric :tags)))
      (reset! test-metric nil)))
  (testing "Process log line with no tags"
    (with-redefs [nimrod.core.metric/compute-metric test-compute-metric]
      (process "log" "[nimrod][1][alert][name][value]")
      (is (= alert (@test-metric :type)))
      (is (= "log" (@test-metric :log)))
      (is (= "1" (@test-metric :timestamp)))
      (is (= "name" (@test-metric :name)))
      (is (= "value" (@test-metric :value)))
      (is (= #{} (@test-metric :tags)))
      (reset! test-metric nil)))
  (testing "No log line processing due to missing prefix"
    (with-redefs [nimrod.core.metric/compute-metric test-compute-metric]
      (process "log" "[1][alert][name][value]")
      (is (nil? @test-metric))
      (reset! test-metric nil)))
  (testing "No log line processing due to bad timestamp"
    (with-redefs [nimrod.core.metric/compute-metric test-compute-metric]
      (process "log" "[nimrod][bad timestamp][alert][name][value]")
      (is (nil? @test-metric))
      (reset! test-metric nil)))
  (testing "No log line processing due to bad metric"
    (with-redefs [nimrod.core.metric/compute-metric test-compute-metric]
      (process "log" "[nimrod][1][bad metric][name][value]")
      (is (nil? @test-metric))
      (reset! test-metric nil))))
