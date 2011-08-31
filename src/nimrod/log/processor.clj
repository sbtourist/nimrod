(ns nimrod.log.processor
 (:use
   [clojure.string :as string :only [split]]
   [clojure.contrib.logging :as log]
   [nimrod.core.metrics])
 (:refer-clojure :exclude [split])
 )

(defonce log-pattern (re-pattern ".*\\[nimrod].*\\[(\\d+)\\].*\\[(.+)\\].*\\[(.+)\\].*\\[(.+)\\].*\\[(.*)\\].*"))
(defonce no-tags-log-pattern (re-pattern ".*\\[nimrod].*\\[(\\d+)\\].*\\[(.+)\\].*\\[(.+)\\].*\\[(.+)\\].*"))

(defn- split-match [match r]
  (if (seq match)
    (split match #",")
    nil
    )
  )

(defn- extract [line]
  (if-let [matches (re-matches log-pattern line)]
    (let [matches (vec (rest matches))]
      {:timestamp (matches 0) :metric (matches 1) :name (matches 2)  :value (matches 3) :tags (into #{} (split-match (matches 4) #","))}
      )
    (if-let [matches (re-matches no-tags-log-pattern line)]
      (let [matches (vec (rest matches))]
        {:timestamp (matches 0) :metric (matches 1) :name (matches 2)  :value (matches 3) :tags #{}}
        )
      nil)
    )
  )

(defn- type-of [metric]
  (condp = metric
    "alert" alert
    "gauge" gauge
    "counter" counter
    "timer" timer
    nil
    )
  )

(defn process [log line]
  (try
    (if-let [extracted (extract line)]
      (if-let [metric (type-of (extracted :metric))]
        (set-metric metric log (extracted :name) (extracted :timestamp) (extracted :value) (extracted :tags))
        (log/warn (str "No metric with name: " (extracted :metric)))
        )
      )
    (catch Exception ex (log/error (.getMessage ex) ex))
    )
  )