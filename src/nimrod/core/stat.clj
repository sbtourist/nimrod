(ns nimrod.core.stat)

(defn average [samples previous-average value]
  (if (> samples 0)
    (+ previous-average (/ (- value previous-average) samples))
    0))

(defn median [samples]
  (let [size (count samples)]
    (if (> size 0)
      (if (= 0 (mod size 2)) 
        (get samples (- (/ size 2) 1))
        (get samples (/ (- size 1) 2)))
      0)))

(defn variance [samples previous-variance previous-average current-average value]
  (if (> samples 1)
    (/ (+ previous-variance (* (- value previous-average) (- value current-average))) (dec samples))
    0))