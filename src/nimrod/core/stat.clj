(ns nimrod.core.stat)

(defn average [samples previous-average value]
  (if (> samples 0)
    (+ previous-average (/ (- value previous-average) samples))
    0))

(defn variance [samples previous-variance previous-average current-average value]
  (if (> samples 1)
    (/ (+ previous-variance (* (- value previous-average) (- value current-average))) (dec samples))
    0))

(defn percentiles [samples values percentages]
  (into {} 
    (for [p percentages]
      (let [rank (dec (int (+ (* (/ p 100) samples) 0.5)))] [(keyword (str p "th")) (nth values rank)]))))