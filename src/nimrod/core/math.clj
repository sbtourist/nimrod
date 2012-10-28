(ns nimrod.core.math
 (:require [nimrod.core.util :as util]))

(defn count-mean-variance [read-fn]
  (loop [sample 1 mean 0 variance 0]
    (if-let [current-value (read-fn)]
      (let [
        current-mean (+ mean (/ (- current-value mean) sample))
        current-variance (+ variance (* (- current-value mean) (- current-value current-mean)))]
        (recur (inc sample) current-mean current-variance))
      [(dec sample)
      (util/unrationalize mean) 
      (if (<= sample 2) 0 (util/unrationalize (/ variance (- sample 2))))])))

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
