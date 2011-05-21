(ns nimrod.core.stat-test
 (:use
   [clojure.test]
   [nimrod.core.stat]
   )
 )

(deftest compute-average
  (is (= 1 (average 1 0 1)))
  (is (= 2 (average 2  1 3)))
  (is (= 3 (average 3  2 5)))
  )

(deftest compute-variance
  (is (= 0 (variance 1 0 0 0 2)))
  (is (= 0 (variance 2 0 0 2 2)))
  (is (= 0 (variance 3 0 2 2 2)))
  (is (= 4 (variance 4 0 2 3 6)))
  )

(deftest compute-std-deviation
  (is (= 0 (std-deviation 1 0 0 0 2)))
  (is (= 0 (std-deviation 2 0 0 2 2)))
  (is (= 0 (std-deviation 3 0 2 2 2)))
  (is (= 2 (std-deviation 4 0 2 3 6)))
  )