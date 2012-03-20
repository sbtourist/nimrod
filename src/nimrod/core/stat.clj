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
    (index-fn (int (java.lang.Math/floor (/ total 2))))
    (util/unrationalize (/ (+ (index-fn (dec (/ total 2))) (index-fn (/ total 2))) 2))))

(defn percentiles [total percentages index-fn]
  (into {} 
    (for [p percentages]
      (if (and (> p 0) (<= p 100))
        (let [rank (int (+ (* (/ p 100) total) 0.5))] [(keyword (str p "th")) (index-fn (dec rank))])
        (throw (IllegalArgumentException. (str "Out of bounds percentage: " p)))))))