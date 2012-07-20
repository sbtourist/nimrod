(ns nimrod.internal.stats-test
  (:use
    [clojure.test]
    [nimrod.core.util]
    [nimrod.internal.stats]))

(deftest stats
  (update-rate-stats :test (clock) (seconds 1))
  (update-rate-stats :test (clock) (seconds 1))
  (update-rate-stats :test (clock) (seconds 1))
  (Thread/sleep 250)
  (testing "Updated"
    (is (= 3 ((show-stats) :test))))
  (update-rate-stats :test (+ (clock) (seconds 2)) (seconds 1))
  (Thread/sleep 250)
  (testing "Restarted"
    (is (= 1 ((show-stats) :test))))
  (with-redefs [nimrod.core.util/clock (fn [] (+ (System/currentTimeMillis) (minutes 1)))]
    (refresh-rate-stats :test (seconds 1))
    (Thread/sleep 250)
    (testing "Refreshed"
      (is (= 0 ((show-stats) :test))))))
