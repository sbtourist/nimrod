(ns nimrod.internal.stats
  (:use
    [nimrod.core.util]))

(defonce stats-agent (new-agent {:stats {} :tmp {}}))

(defn update-rate-stats [k t]
  (send stats-agent 
    (fn [state t]
      (let [rate (get-in state [:stats k] 0)
            timestamp (get-in state [:tmp k :timestamp] 0)]
        (if (<= (- t timestamp) (seconds 1))
          (assoc-in state [:stats k] (inc rate))
          (-> state
            (assoc-in [:stats k] 1)
            (assoc-in [:tmp k :timestamp] t)))))
    t))

(defn refresh-rate-stats [k]
  (await-for 1000 (send stats-agent 
    (fn [state]
      (let [now (clock)
            timestamp (get-in state [:tmp k :timestamp] 0)]
        (if (> (- now timestamp) (seconds 1))
          (assoc-in state [:stats k] 0)
          state))))))

(defn show-stats []
  (@stats-agent :stats))
