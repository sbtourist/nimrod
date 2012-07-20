(ns nimrod.core.math
 (:require [nimrod.core.util :as util]))

(defn mean [samples previous-mean value]
  (if (> samples 0)
    (util/unrationalize (+ previous-mean (/ (- value previous-mean) samples)))
    0))

(defn variance [samples previous-variance previous-mean current-mean value]
  (if (> samples 1)
    (util/unrationalize (/ (+ previous-variance (* (- value previous-mean) (- value current-mean))) (dec samples)))
    0))

(defn median [total read-fn]
  (if (odd? total)
    (read-fn (Math/round (/ total 2.0)))
    (util/unrationalize (/ (+ (read-fn (inc (/ total 2))) (read-fn (/ total 2))) 2))))

(defn percentiles [total percentages read-fn]
  (into {} 
    (for [p percentages]
      (cond 
        (and (> p 0) (< p 100)) (let [rank (Math/round (+ (* (/ p 100) total) 0.5))] [(keyword (str p "th")) (read-fn rank)])
        (= 0 p) [(keyword (str p "th")) (read-fn 1)]
        (= 100 p) [(keyword (str p "th")) (read-fn total)]
        :else (throw (IllegalArgumentException. (str "Out of bounds percentile: " p)))))))
