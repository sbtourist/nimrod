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

(defn median [values index-fn]
  (let [total (count values)]
    (if (odd? total)
      (index-fn values (int (java.lang.Math/floor (/ total 2))))
      (util/unrationalize (/ (+ (index-fn values (dec (/ total 2))) (index-fn values (/ total 2))) 2)))))

(defn percentiles [values percentages]
  (let [total (count values)]
    (into {} 
      (for [p percentages]
        (let [rank (int (+ (* (/ p 100) total) 0.5))] [(keyword (str p "th")) (nth values (dec rank))])))))