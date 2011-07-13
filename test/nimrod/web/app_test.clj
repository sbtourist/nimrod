(ns nimrod.web.app-test
 (:use
   [clojure.test]
   [clojure.contrib.mock]
   [clojure.contrib.json]
   [ring.mock.request]
   [nimrod.web.app]
   )
 )

(deftest add-log
  (expect [nimrod.log.tailer/start-tailer (has-args ["log" 1000] (times 1 (returns "1")))]
    (is (= {:1 "log"} (read-json ((nimrod-app (request :post "/logs" {"file" "log" "interval" "1000"})) :body))))
    )
  )

(deftest list-logs
  (expect [nimrod.log.tailer/list-tailers (has-args [] (times 1 (returns {"1" "log"})))]
    (is (= {:1 "log"} (read-json ((nimrod-app (request :get "/logs")) :body))))
    )
  )

(deftest delete-log
  (expect [nimrod.log.tailer/stop-tailer (has-args ["1"] (times 1))]
    (nimrod-app (request :delete "/logs/1"))
    )
  )

(deftest list-metrics-for-type
  (let [gauge (reify nimrod.core.metrics.MetricProtocol (list-metrics [this log-id tags] (is (= "1" log-id)) ["gauge1"]))]
    (binding [nimrod.core.metrics/metric-types {:gauge gauge}]
      (is (= ["gauge1"] (read-json ((nimrod-app (request :get "/logs/1/gauges")) :body))))
      )
    )
  )

(deftest delete-metrics-by-age
  (let [gauge (reify nimrod.core.metrics.MetricProtocol (expire-metrics [this log-id age] (is (= "1" log-id)) (is (= 0 age))))]
    (binding [nimrod.core.metrics/metric-types {:gauge gauge}]
      (is (= 204 ((nimrod-app (request :delete "/logs/1/gauges" {:age 0})) :status)))
      )
    )
  )

(deftest delete-metrics-by-tags
  (let [gauge (reify nimrod.core.metrics.MetricProtocol (remove-metrics [this log-id tags] (is (= "1" log-id)) (is (= #{"tag1"} tags))))]
    (binding [nimrod.core.metrics/metric-types {:gauge gauge}]
      (is (= 204 ((nimrod-app (request :delete "/logs/1/gauges" {:tags "tag1"})) :status)))
      )
    )
  )

(deftest read-metric
  (let [gauge (reify nimrod.core.metrics.MetricProtocol (read-metric [this log-id metric-id] (is (= "1" log-id)) (is (= "m" metric-id)) {:value "value"}))]
    (binding [nimrod.core.metrics/metric-types {:gauge gauge}]
      (is (= {:value "value"} (read-json ((nimrod-app (request :get "/logs/1/gauges/m")) :body))))
      )
    )
  )

(deftest delete-metric
  (let [gauge (reify nimrod.core.metrics.MetricProtocol (remove-metric [this log-id metric-id] (is (= "1" log-id)) (is (= "m" metric-id))))]
    (binding [nimrod.core.metrics/metric-types {:gauge gauge}]
      (is (= 204 ((nimrod-app (request :delete "/logs/1/gauges/m")) :status)))
      )
    )
  )

(deftest reset-history
  (let [gauge (reify nimrod.core.metrics.MetricProtocol (reset-history [this log-id metric-id limit] (is (= "1" log-id)) (is (= "m" metric-id)) (is (= 1 limit))))]
    (binding [nimrod.core.metrics/metric-types {:gauge gauge}]
      (is (= 204 ((nimrod-app (request :post "/logs/1/gauges/m/history" {:limit "1"})) :status)))
      )
    )
  )

(deftest read-history
  (let [gauge (reify nimrod.core.metrics.MetricProtocol (read-history [this log-id metric-id tags] (is (= "1" log-id)) (is (= "m" metric-id)) {:value "value"}))]
    (binding [nimrod.core.metrics/metric-types {:gauge gauge}]
      (is (= {:value "value"} (read-json ((nimrod-app (request :get "/logs/1/gauges/m/history")) :body))))
      )
    )
  )