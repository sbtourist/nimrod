(ns nimrod.log.processor
 (:use
   [clojure.contrib.logging :as log])
 (:refer-clojure :exclude [spit])
 )

(defonce log-pattern (re-pattern ".*\\[(\\d+)\\].*\\[(.+)\\].*\\[(.+)\\].*\\[(.+)\\].*"))

(defn- extract [line]
  (let [matches (into [] (rest (re-matches log-pattern line)))]
    (if (seq matches)
      {:timestamp (matches 0) :metric (matches 1) :name (matches 2)  :value (matches 3)}
      nil
      )
    )
  )

(defn process [line metrics]
  (if-let [values (extract line)]
    (if-let [metric (metrics (values :metric))]
      (metric (values :name) (values :timestamp) (values :value))
      (log/warn (str "No metric with name: " (values :metric)))
      )
    )
  )