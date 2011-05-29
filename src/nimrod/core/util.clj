(ns nimrod.core.util
 (:use [clojure.contrib.logging :as log])
 )

(defn new-agent [state]
  (let [a (agent state)]
    (set-error-handler! a #(log/log :error (.getMessage %2) %2))
    (set-error-mode! a :continue)
    a
    )
  )

(defn unrationalize [n] (if (ratio? n) (float n) n))

(defn unrationalize-all [m] (reduce conj {} (map #(into [] [%1 (unrationalize %2)]) (keys m) (vals m))))