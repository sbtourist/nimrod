(ns nimrod.core.history
 (:use [nimrod.core.util])
 )

(defonce default-history-age (atom (days 1)))

(defn- smart-remove [satisfies? coll]
  (if (seq coll) 
    (loop [index 0]
      (if (< index (count coll))
        (if (satisfies? (coll index))
          (recur (inc index))
          (subvec coll index)
          )
        []
        )
      )
    coll
    )
  )

(defn- expire [values age]
  (let [now (System/currentTimeMillis) expired? #(<= age (- now (string-to-date (%1 :date))))]
    (smart-remove expired? values)
    )
  )

(defn set-default-history-age [age] 
  (reset! default-history-age age)
  )

(defn create-history
  ([]
    {:age @default-history-age :size 0 :values []})
  ([age]
    {:age age :size 0 :values []})
  ([age value]
    {:age age :size 1 :values [value]})
  )

(defn update-history [history value]
  (let [age (history :age) values (conj (expire (history :values) age) value) size (count values)]
    (assoc history :values values :size size)
    )
  )