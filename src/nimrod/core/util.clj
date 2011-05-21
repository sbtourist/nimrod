(ns nimrod.core.util
 (:use [clojure.contrib.logging :as log])
 (:import [java.util Date])
 )

(defn date []
  (Date.)
  )

(defn date-in-millis []
  (.getTime (Date.))
  )

(defn new-agent [state]
  (let [a (agent state)]
    (set-error-handler! a #(log/log :error (.getMessage %2) %2))
    (set-error-mode! a :continue)
    a
    )
  )