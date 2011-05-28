(ns nimrod.web.app
 (:use
   [clojure.contrib.logging :as log]
   [clojure.contrib.json :as json]
   [compojure.core :as http]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [nimrod.core.metrics]
   [nimrod.log.tailer])
 )

(defonce response-codes {:ok 200 :no-content 204 :not-found 404 :error 500})

(defn- response 
  ([status body]
    {:headers {"Content-Type" "application/json"} :status (response-codes status) :body (json/json-str body)}
    )
  ([status]
    {:headers {"Content-Type" "application/json"} :status (response-codes status)}
    )
  )

(defn- wrap-errors [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception ex
        (log/error (.getMessage ex) ex)
        (response :error {:error (.getMessage ex)}))
      )
    )
  )

(http/defroutes nimrod-routes
  (http/POST "/logs" [file interval]
    (let [tailer (start-tailer file (Long/parseLong interval))]
      (response :ok {tailer file})
      )
    )
  (http/GET "/logs" []
    (response :ok (list-tailers))
    )
  (http/DELETE "/logs/:log-id" [log-id]
    (stop-tailer log-id)
    (response :no-content)
    )
  (http/GET "/logs/:log-id/:metric-type" [log-id metric-type]
    (if-let [metric (metric-types (keyword metric-type))]
      (if-let [result (list-metrics metric log-id)]
        (response :ok result)
        (response :not-found)
        )
      (response :error {:error (str "Bad metric type: " metric-type)})
      )
    )
  (http/GET "/logs/:log-id/:metric-type/:metric-id" [log-id metric-type metric-id]
    (if-let [metric (metric-types (keyword metric-type))]
      (if-let [result (read-metric metric log-id metric-id)]
        (response :ok result)
        (response :not-found)
        )
      (response :error {:error (str "Bad metric type: " metric-id)})
      )
    )
  (http/POST "/logs/:log-id/:metric-type/:metric-id/history" [log-id metric-type metric-id limit]
    (if-let [metric (metric-types (keyword metric-type))]
      (do 
        (reset-history metric log-id metric-id (Long/parseLong limit))
        (response :no-content)
        )
      (response :error {:error (str "Bad metric type: " metric-id)})
      )
    )
  (http/GET "/logs/:log-id/:metric-type/:metric-id/history" [log-id metric-type metric-id]
    (if-let [metric (metric-types (keyword metric-type))]
      (if-let [result (read-history metric log-id metric-id)]
        (response :ok result)
        (response :not-found)
        )
      (response :error {:error (str "Bad metric type: " metric-id)})
      )
    )
  (route/not-found "")
  )

(defonce nimrod-app
  (handler/api (wrap-errors nimrod-routes))
  )