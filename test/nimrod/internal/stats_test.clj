(ns nimrod.internal.stats-test
  (:use
    [clojure.test]
    [nimrod.core.util]
    [nimrod.internal.stats]))

(deftest stats
  (testing "Equals the number of updates (3) times 1000/250"
    (update-rate-stats :test (clock) (seconds 1))
    (update-rate-stats :test (clock) (seconds 1))
    (update-rate-stats :test (clock) (seconds 1))
    (Thread/sleep 250)
    (is (= 12 ((show-stats [:test] (clock) (seconds 1)) :test))))
  (testing "Zero-ed"
    (is (= 0 ((show-stats [:test] (+ (clock) (seconds 10)) (seconds 1)) :test)))))
