(ns nimrod.core.util
 (:require 
   [clojure.tools.logging :as log])
 (:import 
   [java.text SimpleDateFormat] [java.util Date] [java.util.concurrent TimeUnit]))

(def simple-date-format (proxy [ThreadLocal] [] (initialValue [] (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssz"))))

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
