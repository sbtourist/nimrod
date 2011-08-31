(ns nimrod.core.history)

(defn create-history
  ([limit]
    {:limit limit :size 0 :values []})
  ([limit value]
    {:limit limit :size 1 :values [value]})
  )

(defn update-history [history value]
  (let [limit (history :limit) values (history :values) size (count values)]
    (if (= size limit)
      (let [new-values (conj (apply vector (rest values)) value)]
        (assoc history :values new-values :size (count new-values))
        )
      (let [new-values (conj values value)]
        (assoc history :values new-values :size (count new-values))
        )
      )
    )
  )