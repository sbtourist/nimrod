(ns nimrod.core.math-test
 (:use
   [clojure.test]
   [nimrod.core.math]))

(deftest compute-mean
  (is (= 1 (mean 1 0 1)))
  (is (= 2 (mean 2  1 3)))
  (is (= 3 (mean 3  2 5))))

(deftest compute-variance
  (is (= 0 (variance 1 0 0 0 2)))
  (is (= 0 (variance 2 0 0 2 2)))
  (is (= 0 (variance 3 0 2 2 2)))
  (is (= 4 (variance 4 0 2 3 6))))

(deftest compute-percentiles
  (is (= {:50th 3 :75th 4} (percentiles 5 [50 75] #(nth [1 2 3 4 5] (dec %1))))))

(deftest compute-percentiles-at-bounds
  (is (= {:0th 1 :100th 5} (percentiles 5 [0 100] #(nth [1 2 3 4 5] (dec %1))))))

(deftest compute-odd-median 
  (is (= 3 (median 5 #(nth [1 2 3 4 5] (dec %1))))))

(deftest compute-even-median 
  (is (= 6 (median 6 #(nth [1 3 5 7 9 11] (dec %1))))))
