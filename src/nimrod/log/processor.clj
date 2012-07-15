(ns nimrod.log.processor
 (:require
   [clojure.string :as string :only [split]]
   [clojure.tools.logging :as log])
 (:use
   [nimrod.core.metric]
   [nimrod.core.util])
 (:refer-clojure :exclude [split]))

(defonce log-pattern (re-pattern ".*?\\[nimrod\\].*?\\[(\\d+?)\\].*?\\[(.+?)\\].*?\\[(.+?)\\].*?\\[(.+?)\\].*?\\[(.*)\\].*"))
(defonce no-tags-log-pattern (re-pattern ".*?\\[nimrod\\].*?\\[(\\d+?)\\].*?\\[(.+?)\\].*?\\[(.+?)\\].*?\\[(.+?)\\].*"))

(defn- type-of [metric]
  (condp = metric
    "alert" alert
    "gauge" gauge
    "counter" counter
    "timer" timer
    nil))

(defn- split-match [match r]
  (when (seq match)
    (string/split match #",")))

(defn- extract [line]
  (if-let [matches (re-matches log-pattern line)]
    (let [matches (vec (rest matches))]
      {:timestamp (matches 0) :metric (matches 1) :name (matches 2)  :value (matches 3) :tags (into #{} (split-match (matches 4) #","))})
    (when-let [matches (re-matches no-tags-log-pattern line)]
      (let [matches (vec (rest matches))]
        {:timestamp (matches 0) :metric (matches 1) :name (matches 2)  :value (matches 3) :tags #{}}))))

(defn process [log line]
  (try
    (if-let [extracted (extract line)]
      (if-let [metric (type-of (extracted :metric))]
        (do (compute-metric metric log (extracted :name) (extracted :timestamp) (extracted :value) (extracted :tags)) true)
        (do (log/warn (str "No metric with name: " (extracted :metric))) false))
      false)
    (catch Exception ex (do (log/error (.getMessage ex) ex) false))))
