(ns nimrod.web.app-test
 (:use
   [clojure.test]
   [clojure.contrib.json]
   [ring.mock.request]
   [nimrod.web.app]
   )
 )

(deftest add-log
  (letfn [(mocked-start-tailer [log interval] (is (= "log" log)) (is (= 1000 interval)) "1")]
    (binding [nimrod.log.tailer/start-tailer mocked-start-tailer]
      (is (= {:1 "log"} (read-json ((nimrod-app (request :post "/logs" {"file" "log" "interval" "1000"})) :body))))
      )
    )
  )

(deftest list-logs
  (letfn [(mocked-list-tailers [] {"1" "log"})]
    (binding [nimrod.log.tailer/list-tailers mocked-list-tailers]
      (is (= {:1 "log"} (read-json ((nimrod-app (request :get "/logs")) :body))))
      (is (= "/logs" (((nimrod-app (request :get "/logs/")) :headers) "Location")))
      )
    )
  )

(deftest delete-log
  (letfn [(mocked-stop-tailer [log-id] (is (= "1" log-id)))]
    (binding [nimrod.log.tailer/stop-tailer mocked-stop-tailer]
      (nimrod-app (request :delete "/logs/1"))
      )
    )
  )

(deftest list-metrics-for-type
  (let [gauge (reify nimrod.core.metrics.MetricProtocol (list-metrics [this log-id tags] (is (= "1" log-id)) ["gauge1"]))]
    (binding [nimrod.core.metrics/metric-types {:gauge gauge}]
      (is (= ["gauge1"] (read-json ((nimrod-app (request :get "/logs/1/gauges")) :body))))
      (is (= "/logs/1/gauges" (((nimrod-app (request :get "/logs/1/gauges/")) :headers) "Location")))
      )
    )
  )

(deftest list-metrics-for-type-by-tags
  (let [gauge (reify nimrod.core.metrics.MetricProtocol (list-metrics [this log-id tags] (is (= "1" log-id)) (is (= #{"tag1"} tags)) ["gauge1"]))]
    (binding [nimrod.core.metrics/metric-types {:gauge gauge}]
      (is (= ["gauge1"] (read-json ((nimrod-app (request :get "/logs/1/gauges" {:tags "tag1"})) :body))))
      (is (= "/logs/1/gauges" (((nimrod-app (request :get "/logs/1/gauges/" {:tags "tag1"})) :headers) "Location")))
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