(ns nimrod.core.stat
 (:require [nimrod.core.util :as util]))

(defn average [samples previous-average value]
  (if (> samples 0)
    (util/unrationalize (+ previous-average (/ (- value previous-average) samples)))
    0))

(defn variance [samples previous-variance previous-average current-average value]
  (if (> samples 1)
    (util/unrationalize (/ (+ previous-variance (* (- value previous-average) (- value current-average))) (dec samples)))
    0))

(defn median [total index-fn]
  (if (odd? total)
    (index-fn (Math/round (/ total 2.0)))
    (util/unrationalize (/ (+ (index-fn (inc (/ total 2))) (index-fn (/ total 2))) 2))))

(defn percentiles [total percentages index-fn]
  (into {} 
    (for [p percentages]
      (cond 
        (and (> p 0) (< p 100)) (let [rank (Math/round (+ (* (/ p 100) total) 0.5))] [(keyword (str p "th")) (index-fn rank)])
        (= 0 p) [(keyword (str p "th")) (index-fn 1)]
        (= 100 p) [(keyword (str p "th")) (index-fn total)]
        :else (throw (IllegalArgumentException. (str "Out of bounds percentile: " p)))))))