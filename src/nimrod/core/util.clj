(ns nimrod.core.util
 (:require 
   [clojure.tools.logging :as log])
 (:import 
   [java.text SimpleDateFormat] [java.util Date] [java.util.concurrent TimeUnit]))

(defonce simple-date-format (proxy [ThreadLocal] [] (initialValue [] (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssz"))))
(defonce num-pattern (re-pattern "(\\d+)"))
(defonce age-date-pattern (re-pattern "(\\d+)([smhd]{1})"))
(defonce past-date-pattern (re-pattern "(\\d+)([smhd]{1})\\.ago"))

(defn new-agent [state]
  (let [a (agent state)]
    (set-error-handler! a #(log/log :error (.getMessage %2) %2))
    (set-error-mode! a :continue)
    a))

(defn date-to-string [t]
  (.format (.get simple-date-format) (Date. t)))

(defn string-to-date [d]
  (.getTime (.parse (.get simple-date-format) d)))

(defn seconds [t] (.toMillis (TimeUnit/SECONDS) t))

(defn minutes [t] (.toMillis (TimeUnit/MINUTES) t))

(defn hours [t] (.toMillis (TimeUnit/HOURS) t))

(defn days [t] (.toMillis (TimeUnit/DAYS) t))

(defn clock [] (System/currentTimeMillis))

(defn unrationalize [n] (if (ratio? n) (float n) n))

(defn- to-millis [t unit]
  (cond
    (= "s" unit) (seconds t)
    (= "m" unit) (minutes t)
    (= "h" unit) (hours t)
    (= "d" unit) (days t)
    :else (throw (IllegalArgumentException. (str "Bad time unit: " unit)))))

(defn age-to-millis [age]
  (when-not (nil? age)
    (if-let [matches (re-matches num-pattern age)]
      (Long/parseLong (nth matches 1))
      (if-let [matches (re-matches age-date-pattern age)]
        (to-millis (Long/parseLong (nth matches 1)) (nth matches 2))
        (throw (IllegalArgumentException. (str "Bad value: " age)))))))

(defn time-to-millis [now t]
  (when-not (nil? t)
    (if-let [matches (re-matches num-pattern t)]
      (Long/parseLong (nth matches 1))
      (if-let [matches (re-matches past-date-pattern t)]
        (- now (to-millis (Long/parseLong (nth matches 1)) (nth matches 2)))
        (throw (IllegalArgumentException. (str "Bad value: " t)))))))
