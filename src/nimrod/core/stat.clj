(ns nimrod.core.stat
 (:use [clojure.contrib.math :as math])
 )

(defn average [samples previous-average value]
  (+ previous-average (/ (- value previous-average) samples))
  )

(defn variance [samples previous-variance previous-average current-average value]
  (if (not (= 1 samples))
    (/ (+ previous-variance (* (- value previous-average) (- value current-average))) (dec samples))
    0
    )
  )

(defn std-deviation [samples previous-variance previous-average current-average value]
  (math/sqrt (variance samples previous-variance previous-average current-average value))
  )