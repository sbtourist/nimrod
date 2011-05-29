(ns nimrod.core.stat
 (:use [clojure.contrib.math :as math])
 )

(defn average [samples previous-average value]
  (if (> samples 0)
    (+ previous-average (/ (- value previous-average) samples))
    0
    )
  )

(defn variance [samples previous-variance previous-average current-average value]
  (if (> samples 1)
    (/ (+ previous-variance (* (- value previous-average) (- value current-average))) (dec samples))
    0
    )
  )

(defn std-deviation [samples previous-variance previous-average current-average value]
  (math/sqrt (variance samples previous-variance previous-average current-average value))
  )