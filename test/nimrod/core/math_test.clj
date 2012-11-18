(ns nimrod.core.math-test
 (:use
   [clojure.test]
   [nimrod.core.math]))

(deftest compute-ewma
  (testing "ewma from 0"
    (loop [ewma1 nil i 1]
      (if (< i 1000)
        (recur (ewma ewma1 1 i) (inc i))
    (println ewma1))))
  (testing "ewma within 1000 values"
    (loop [ewma1 [1000 10000 100000] i 1]
      (if (< i 1000)
        (recur (ewma ewma1 1 i) (inc i))
    (println ewma1))))
  (testing "ewma within 10000 values"
    (loop [ewma1 [1000 10000 100000] i 1]
      (if (< i 10000)
        (recur (ewma ewma1 1 i) (inc i))
    (println ewma1))))
  (testing "ewma within 100000 values"
    (loop [ewma1 [1000 10000 100000] i 1]
      (if (< i 100000)
        (recur (ewma ewma1 1 i) (inc i))
    (println ewma1)))))

(deftest compute-count-mean-variance
  (testing "count-mean-variance with 1 value"
    (let [idx (atom -1) cmv (count-mean-variance #(get [1] (swap! idx inc)))]
      (is (= 1 (cmv 0)))
      (is (= 1 (cmv 1)))
      (is (= 0 (cmv 2)))))
  (testing "count-mean-variance with many values"
    (let [idx (atom -1) cmv (count-mean-variance #(get [1 2 3 4 5 6 7 8] (swap! idx inc)))]
      (is (= 8 (cmv 0)))
      (is (= 4.5 (cmv 1)))
      (is (= 6 (cmv 2))))))

(deftest compute-percentiles
  (is (= {:50th 3 :75th 4} (percentiles 5 [50 75] #(nth [1 2 3 4 5] (dec %1))))))

(deftest compute-percentiles-at-bounds
  (is (= {:0th 1 :100th 5} (percentiles 5 [0 100] #(nth [1 2 3 4 5] (dec %1))))))

(deftest compute-odd-median 
  (is (= 3 (median 5 #(nth [1 2 3 4 5] (dec %1))))))

(deftest compute-even-median 
  (is (= 6 (median 6 #(nth [1 3 5 7 9 11] (dec %1))))))
