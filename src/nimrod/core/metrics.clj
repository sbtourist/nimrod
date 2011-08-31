(ns nimrod.core.metrics
 (:use
   [clojure.set :as cset]
   [nimrod.core.history]
   [nimrod.core.stat]
   [nimrod.core.types]
   [nimrod.core.util])
 )

(defn- get-metric [values metric-ns metric-id]
  ((get @values metric-ns {}) metric-id)
  )

(defn- create-metric []
  (new-agent {:history (create-history 100) :computed-value nil :displayed-value nil :update-time nil})
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

(defn set-metric [{type :type values :values} metric-ns metric-id timestamp value tags]
  (dosync
    (let [metric (get-or-create-metric values metric-ns metric-id)]
      (send metric (fn [current _] (let [t (System/currentTimeMillis)
                                         computed (compute type metric-id timestamp (current :computed-value) value tags)
                                         displayed (display computed t)
                                         history (update-history (current :history) displayed)]
                                     {:history history :computed-value computed :displayed-value displayed :update-time t}
                                     )) nil
        )
      )
    )
  )

(defn read-metric [{type :type values :values} metric-ns metric-id]
  (dosync
    (if-let [metric (get-metric values metric-ns metric-id)]
      (@metric :displayed-value)
      nil
      )
    )
  )

(defn remove-metric [{type :type values :values} metric-ns metric-id]
  (dosync
    (when-let [metrics-in-ns (get-metrics-in-ns values metric-ns)]
      (alter values conj [metric-ns (dissoc metrics-in-ns metric-id)])
      )
    )
  )

(defn list-metrics [{type :type values :values} metric-ns tags]
  (dosync
    (if-let [metrics-in-ns (get-metrics-in-ns values metric-ns)]
      (if (seq tags)
        (apply vector (sort (keys (into {} (filter #(cset/subset? tags ((@(second %1) :computed-value) :tags)) metrics-in-ns)))))
        (apply vector (sort (keys metrics-in-ns)))
        )
      []
      )
    )
  )

(defn remove-metrics [{type :type values :values} metric-ns tags]
  (dosync
    (when-let [metrics-in-ns (get-metrics-in-ns values metric-ns)]
      (alter values conj [metric-ns (into {} (filter #(not (cset/subset? tags ((@(second %1) :computed-value) :tags))) metrics-in-ns))])
      )
    )
  )

(defn expire-metrics [{type :type values :values} metric-ns age]
  (dosync
    (when-let [metrics-in-ns (get-metrics-in-ns values metric-ns)]
      (alter values conj [metric-ns (into {} (filter #(< (- (System/currentTimeMillis) (@(second %1) :update-time)) age) metrics-in-ns))])
      )
    )
  )

(defn read-history [{type :type values :values} metric-ns metric-id tags]
  (dosync
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

(defn reset-history [{type :type values :values} metric-ns metric-id limit]
  (dosync
    (let [metric (get-or-create-metric values metric-ns metric-id)]
      (send metric conj {:history (create-history limit)})
      )
    )
  )

(defonce alerts {:type (new-alert) :values (ref {})})
(defonce gauges {:type (new-gauge) :values (ref {})})
(defonce counters {:type (new-counter) :values (ref {})})
(defonce timers {:type (new-timer) :values (ref {})})
(defonce metrics {
                  :alert alerts
                  :gauge gauges
                  :counter counters
                  :timer timers
                  :alerts alerts
                  :gauges gauges
                  :counters counters
                  :timers timers
                  })