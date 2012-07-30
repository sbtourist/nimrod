(ns nimrod.internal.stats
  (:use [nimrod.core.util]))

(defonce stats-agent (new-agent {:stats {} :tmp {}}))

(defn update-rate-stats [k t f]
  (send stats-agent 
    (fn [state]
      (let [rate (get-in state [:stats k] 0)
            timestamp (get-in state [:tmp k :timestamp] 0)]
        (if (<= (- t timestamp) f)
          (assoc-in state [:stats k] (inc rate))
          (-> state
            (assoc-in [:stats k] 1)
            (assoc-in [:tmp k :timestamp] t)))))))

(defn show-stats [ks t f]
  (into {} 
    (for [k ks]
      (let [rate (get-in @stats-agent [:stats k] 0)
            timestamp (get-in @stats-agent [:tmp k :timestamp] 0)]
        [k (Math/round (double (* (/ rate (- t timestamp)) f)))]))))
