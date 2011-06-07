(ns nimrod.core.util
 (:use [clojure.contrib.logging :as log])
 (:import [java.text SimpleDateFormat] [java.util Date])
 )

(defn new-agent [state]
  (let [a (agent state)]
    (set-error-handler! a #(log/log :error (.getMessage %2) %2))
    (set-error-mode! a :continue)
    a
    )
  )

(defn current-date-string []
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssz") (Date.))
  )

(defn unrationalize [n] (if (ratio? n) (float n) n))

(defn display [m] (reduce conj (sorted-map :date (current-date-string)) (map #(vector %1 (unrationalize %2)) (keys m) (vals m))))