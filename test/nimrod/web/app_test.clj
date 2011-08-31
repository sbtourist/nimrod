(ns nimrod.web.app-test
 (:use
   [clojure.test]
   [clojure.contrib.json]
   [ring.mock.request]
   [nimrod.core.metrics]
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
  (letfn [(mocked-list-metrics [type log-id tags] (is (= gauge type)) (is (= "1" log-id)) ["gauge1"])]
    (binding [nimrod.core.metrics/list-metrics mocked-list-metrics]
      (is (= ["gauge1"] (read-json ((nimrod-app (request :get "/logs/1/gauges")) :body))))
      (is (= "/logs/1/gauges" (((nimrod-app (request :get "/logs/1/gauges/")) :headers) "Location")))
      )
    )
  )

(deftest list-metrics-for-type-by-tags
  (letfn [(mocked-list-metrics [type log-id tags] (is (= gauge type)) (is (= "1" log-id)) (is (= #{"tag1"} tags)) ["gauge1"])]
    (binding [nimrod.core.metrics/list-metrics mocked-list-metrics]
      (is (= ["gauge1"] (read-json ((nimrod-app (request :get "/logs/1/gauges" {:tags "tag1"})) :body))))
      (is (= "/logs/1/gauges" (((nimrod-app (request :get "/logs/1/gauges/" {:tags "tag1"})) :headers) "Location")))
      )
    )
  )

(deftest delete-metrics-by-age
  (letfn [(mocked-expire-metrics [type log-id age] (is (= gauge type)) (is (= "1" log-id)) (is (= 0 age)))]
    (binding [nimrod.core.metrics/expire-metrics mocked-expire-metrics]
      (is (= 204 ((nimrod-app (request :delete "/logs/1/gauges" {:age 0})) :status)))
      )
    )
  )

(deftest delete-metrics-by-tags
  (letfn [(mocked-remove-metrics [type log-id tags] (is (= gauge type)) (is (= "1" log-id)) (is (= #{"tag1"} tags)))]
    (binding [nimrod.core.metrics/remove-metrics mocked-remove-metrics]
      (is (= 204 ((nimrod-app (request :delete "/logs/1/gauges" {:tags "tag1"})) :status)))
      )
    )
  )

(deftest read-metric-value
  (letfn [(mocked-read-metric [type log-id metric-id] (is (= gauge type)) (is (= "1" log-id)) (is (= "m" metric-id)) {:value "value"})]
    (binding [nimrod.core.metrics/read-metric mocked-read-metric]
      (is (= {:value "value"} (read-json ((nimrod-app (request :get "/logs/1/gauges/m")) :body))))
      )
    )
  )

(deftest delete-metric-value
  (letfn [(mocked-remove-metric [type log-id metric-id] (is (= gauge type)) (is (= "1" log-id)) (is (= "m" metric-id)))]
    (binding [nimrod.core.metrics/remove-metric mocked-remove-metric]
      (is (= 204 ((nimrod-app (request :delete "/logs/1/gauges/m")) :status)))
      )
    )
  )

(deftest reset-metric-history
  (letfn [(mocked-reset-history [type log-id metric-id limit] (is (= gauge type)) (is (= "1" log-id)) (is (= "m" metric-id)) (is (= 1 limit)))]
    (binding [nimrod.core.metrics/reset-history mocked-reset-history]
      (is (= 204 ((nimrod-app (request :post "/logs/1/gauges/m/history" {:limit "1"})) :status)))
      )
    )
  )

(deftest read-metric-history
  (letfn [(mocked-read-history [type log-id metric-id tags] (is (= gauge type)) (is (= "1" log-id)) (is (= "m" metric-id)) {:value "value"})]
    (binding [nimrod.core.metrics/read-history mocked-read-history]
      (is (= {:value "value"} (read-json ((nimrod-app (request :get "/logs/1/gauges/m/history")) :body))))
      )
    )
  )