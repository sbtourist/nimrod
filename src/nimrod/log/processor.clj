(ns nimrod.log.processor
 (:use
   [clojure.contrib.logging :as log]
   [nimrod.core.metrics])
 (:refer-clojure :exclude [spit])
 )

(defonce log-pattern (re-pattern ".*\\[nimrod].*\\[(\\d+)\\].*\\[(.+)\\].*\\[(.+)\\].*\\[(.+)\\].*"))

(defn- extract [line]
  (let [matches (into [] (rest (re-matches log-pattern line)))]
    (if (seq matches)
      {:timestamp (matches 0) :metric (matches 1) :name (matches 2)  :value (matches 3)}
      nil
      )
    )
  )

(defn process [log line metrics]
  (if-let [extracted (extract line)]
    (if-let [metric (metrics (keyword (extracted :metric)))]
      (set-metric metric log (extracted :name) (extracted :timestamp) (extracted :value))
      (log/warn (str "No metric with name: " (extracted :metric)))
      )
    )
  )