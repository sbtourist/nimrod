(ns nimrod.core.stat-test
 (:use
   [clojure.test]
   [nimrod.core.stat]))

(deftest compute-average
  (is (= 1 (average 1 0 1)))
  (is (= 2 (average 2  1 3)))
  (is (= 3 (average 3  2 5))))

(deftest compute-variance
  (is (= 0 (variance 1 0 0 0 2)))
  (is (= 0 (variance 2 0 0 2 2)))
  (is (= 0 (variance 3 0 2 2 2)))
  (is (= 4 (variance 4 0 2 3 6))))

(deftest compute-percentiles
  (is (= {50 3 75 4} (percentiles 5 [50 75]))))

(deftest compute-percentiles
  (is (= {:50th 3 :75th 4} (percentiles [1 2 3 4 5] [50 75]))))

(deftest compute-odd-median 
  (is (= 3 (median [1 2 3 4 5] nth))))

(deftest compute-even-median 
  (is (= 6 (median [1 3 5 7 9 11] nth))))