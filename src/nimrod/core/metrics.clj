(ns nimrod.core.metrics
 (:use
   [clojure.set :as cset]
   [nimrod.core.history]
   [nimrod.core.stat]
   [nimrod.core.types]
   [nimrod.core.util])
 )

(defonce alert (new-alert))
(defonce gauge (new-gauge))
(defonce counter (new-counter))
(defonce timer (new-timer))
(defonce metrics {
                  alert (ref {})
                  gauge (ref {})
                  counter (ref {})
                  timer (ref {})
                  })

(defn- get-metric [values metric-ns metric-id]
  ((get @values metric-ns {}) metric-id)
  )

(defn- create-metric []
  (new-agent {:history (create-history (days 1)) :computed-value nil :displayed-value nil :update-time nil})
  )

(defn- get-or-create-metric [values metric-ns metric-id]
  (if-let [metric ((get @values metric-ns {}) metric-id)]
    metric
    (let [metric (create-metric)]
      (alter values assoc-in [metric-ns metric-id] metric)
      metric
      )
    )
  )

(defn- get-metrics-in-ns [values metric-ns]
  (@values metric-ns)
  )

(defn set-metric [type metric-ns metric-id timestamp value tags]
  (dosync
    (let [values (metrics type) metric (get-or-create-metric values metric-ns metric-id)]
      (send metric (fn [current _] (let [t (System/currentTimeMillis)
                                         computed (compute type metric-id timestamp (current :computed-value) value tags)
                                         displayed (assoc (display computed) :date (date-to-string t))
                                         history (update-history (current :history) displayed)]
                                     {:history history :computed-value computed :displayed-value displayed :update-time t}
                                     )) nil
        )
      )
    )
  )

(defn read-metric [type metric-ns metric-id]
  (dosync
    (let [values (metrics type)]
      (if-let [metric (get-metric values metric-ns metric-id)]
        (@metric :displayed-value)
        nil
        )
      )
    )
  )

(defn remove-metric [type metric-ns metric-id]
  (dosync
    (let [values (metrics type)]
      (when-let [metrics-in-ns (get-metrics-in-ns values metric-ns)]
        (alter values conj [metric-ns (dissoc metrics-in-ns metric-id)])
        )
      )
    )
  )

(defn list-metrics [type metric-ns tags]
  (dosync
    (let [values (metrics type)]
      (if-let [metrics-in-ns (get-metrics-in-ns values metric-ns)]
        (if (seq tags)
          (apply vector (sort (keys (into {} (filter #(cset/subset? tags ((@(second %1) :computed-value) :tags)) metrics-in-ns)))))
          (apply vector (sort (keys metrics-in-ns)))
          )
        []
        )
      )
    )
  )

(defn remove-metrics [type metric-ns tags]
  (dosync
    (let [values (metrics type)]
      (when-let [metrics-in-ns (get-metrics-in-ns values metric-ns)]
        (alter values conj [metric-ns (into {} (filter #(not (cset/subset? tags ((@(second %1) :computed-value) :tags))) metrics-in-ns))])
        )
      )
    )
  )

(defn expire-metrics [type metric-ns age]
  (dosync
    (let [values (metrics type)]
      (when-let [metrics-in-ns (get-metrics-in-ns values metric-ns)]
        (alter values conj [metric-ns (into {} (filter #(< (- (System/currentTimeMillis) (@(second %1) :update-time)) age) metrics-in-ns))])
        )
      )
    )
  )

(defn read-history [type metric-ns metric-id tags]
  (dosync
    (let [values (metrics type)]
      (if-let [metric (get-metric values metric-ns metric-id)]
        (if (seq tags)
          (let [history (@metric :history) filtered-values (filter #(cset/subset? tags (%1 :tags)) (history :values))]
            (assoc history :size (count filtered-values) :values (apply vector filtered-values))
            )
          (@metric :history)
          )
        nil
        )
      )
    )
  )

(defn reset-history [type metric-ns metric-id age]
  (dosync
    (let [values (metrics type)]
      (let [metric (get-or-create-metric values metric-ns metric-id)]
        (send metric conj {:history (create-history age)})
        )
      )
    )
  )