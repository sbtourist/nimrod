(ns nimrod.internal.stats
  (:use [nimrod.core.util]))

(defonce stats-agent (new-agent {:stats {} :tmp {}}))

(defn update-rate-stats [ks t f]
  (send stats-agent 
    (fn [state]
      (let
        [stats-keys (cons :stats ks) 
        tmp-keys (cons :tmp (cons :timestamp ks))
        rate (get-in state stats-keys 0)
        timestamp (get-in state tmp-keys 0)]
        (if (<= (- t timestamp) f)
          (assoc-in state stats-keys (inc rate))
          (-> state
            (assoc-in stats-keys 1)
            (assoc-in tmp-keys t)))))))

(defn show-stats [ksa t f]
  (reduce #(assoc-in %1 (%2 0) (%2 1)) {} 
    (for [ks ksa]
      (let 
        [stats-keys (cons :stats ks) 
        tmp-keys (cons :tmp (cons :timestamp ks))
        rate (get-in @stats-agent stats-keys 0)
        timestamp (get-in @stats-agent tmp-keys 0)]
        [ks (Math/round (double (* (/ rate (- t timestamp)) f)))]))))
