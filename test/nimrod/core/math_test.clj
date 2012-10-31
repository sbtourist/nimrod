(ns nimrod.core.math-test
 (:use
   [clojure.test]
   [nimrod.core.math]))

(deftest compute-ewma
  (testing "ewma with 10 values"
    (let 
      [ewma1 (ewma nil 1 1000191)
    ewma2 (ewma ewma1 23 1160000)
    ewma3 (ewma ewma2 35 1170000)
    ewma4 (ewma ewma3 41 1180000)
    ewma5 (ewma ewma4 55 1190000)
    ewma6 (ewma ewma5 63 1200000)
    ewma7 (ewma ewma6 150 1200001)
    ewma8 (ewma ewma7 85 1200200)
    ewma9 (ewma ewma8 99 1200300)
    ewma10 (ewma ewma9 100 1290000)]
    (println ewma1)
    (println ewma2)
    (println ewma3)
    (println ewma4)
    (println ewma5)
    (println ewma6)
    (println ewma7)
    (println ewma8)
    (println ewma9)
    (println ewma10))))

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
