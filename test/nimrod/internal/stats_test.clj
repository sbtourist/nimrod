(ns nimrod.internal.stats-test
  (:use
    [clojure.test]
    [nimrod.core.util]
    [nimrod.internal.stats]))

(deftest stats
  (testing "Equals the number of updates (3) times 1000/250"
    (update-rate-stats [:test] (clock) (seconds 1))
    (update-rate-stats [:test] (clock) (seconds 1))
    (update-rate-stats [:test] (clock) (seconds 1))
    (Thread/sleep 250)
    (is (= 12 ((show-stats [[:test]] (clock) (seconds 1)) :test))))
  (testing "Zero-ed"
    (is (= 0 ((show-stats [[:test]] (+ (clock) (seconds 10)) (seconds 1)) :test)))))

(deftest multiple-stats
  (testing "Multiple stats"
    (update-rate-stats [:single] (clock) (seconds 1))
    (update-rate-stats [:nested :one] (clock) (seconds 1))
    (update-rate-stats [:nested :two] (clock) (seconds 1))
    (Thread/sleep 250)
    (is (< 0 ((show-stats [[:single] [:nested :one] [:nested :two]] (clock) (seconds 1)) :single)))
    (is (< 0 (((show-stats [[:single] [:nested :one] [:nested :two]] (clock) (seconds 1)) :nested) :one)))
    (is (< 0 (((show-stats [[:single] [:nested :one] [:nested :two]] (clock) (seconds 1)) :nested) :two)))))
