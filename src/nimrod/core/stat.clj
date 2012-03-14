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

(defn percentiles [samples percentages]
  (into {}
    (for [p percentages]
      (let [rank (int (+ (* (/ p 100) samples) 0.5))] [p rank]))))

(defn percentiles [values percentages]
  (let [total (count values)]
    (into {} 
      (for [p percentages]
        (let [rank (int (+ (* (/ p 100) total) 0.5))] [(keyword (str p "th")) (nth values (dec rank))])))))