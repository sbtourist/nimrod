(ns nimrod.web.app
 (:use
   [clojure.string :as string :only [split]]
   [clojure.contrib.logging :as log]
   [clojure.contrib.json :as json]
   [compojure.core :as http]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [nimrod.core.metrics]
   [nimrod.log.tailer])
 )

(defonce response-codes {:ok 200 :no-content 204 :not-found 404 :error 500})
(defonce response-headers {"Content-Type" "application/json"})
(defonce cors-response-headers {"Content-Type" "application/json" "Access-Control-Allow-Origin" "*"})
(defonce metrics {
                  "alerts" :alert
                  "gauges" :gauge
                  "counters" :counter
                  "timers" :timer
                  })

(defn- extract [metric-type]
  (metrics metric-type)
  )

(defn- cors-response 
  ([status body]
    {:headers cors-response-headers :status (response-codes status) :body (json/json-str body)}
    )
  ([status]
    {:headers cors-response-headers :status (response-codes status)}
    )
  )

(defn- response 
  ([status body]
    {:headers response-headers :status (response-codes status) :body (json/json-str body)}
    )
  ([status]
    {:headers response-headers :status (response-codes status)}
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

(defn- extract-tags [tags]
  (when (seq tags) (into #{} (string/split tags #",")))
  )

(http/defroutes nimrod-routes

  (http/POST "/logs" [file interval]
    (let [tailer (start-tailer file (Long/parseLong (or interval "1000")))]
      (response :ok {tailer file})
      )
    )
  (http/GET "/logs" []
    (cors-response :ok (list-tailers))
    )
  (http/DELETE "/logs/:log-id" [log-id]
    (stop-tailer log-id)
    (response :no-content)
    )

  (http/GET ["/logs/:log-id/:metric-type" :tags #"[^/?#]+"] [log-id metric-type tags]
    (if-let [metric (metric-types (extract metric-type))]
      (if-let [result (list-metrics metric log-id (extract-tags tags))]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )
  (http/DELETE ["/logs/:log-id/:metric-type" :age #"\d+" :tags #"[^/?#]+"] [log-id metric-type age tags]
    (if-let [metric (metric-types (extract metric-type))]
      (do
        (if (not (nil? age))
          (expire-metrics metric log-id (Long/parseLong age))
          (remove-metrics metric log-id (extract-tags tags))
          )
        (response :no-content)
        )
      (response :error {:error (str "Bad metric type: " metric-type)})
      )
    )

  (http/GET ["/logs/:log-id/:metric-type/:metric-id" :metric-id #"[^/?#]+"] [log-id metric-type metric-id]
    (if-let [metric (metric-types (extract metric-type))]
      (if-let [result (read-metric metric log-id metric-id)]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )
  (http/DELETE ["/logs/:log-id/:metric-type/:metric-id" :metric-id #"[^/?#]+"] [log-id metric-type metric-id]
    (if-let [metric (metric-types (extract metric-type))]
      (do
        (remove-metric metric log-id metric-id)
        (response :no-content)
        )
      (response :error {:error (str "Bad metric type: " metric-type)})
      )
    )

  (http/POST ["/logs/:log-id/:metric-type/:metric-id/history" :metric-id #"[^/?#]+"] [log-id metric-type metric-id limit]
    (if-let [metric (metric-types (extract metric-type))]
      (do 
        (reset-history metric log-id metric-id (Long/parseLong limit))
        (response :no-content)
        )
      (response :error {:error (str "Bad metric type: " metric-type)})
      )
    )
  (http/GET ["/logs/:log-id/:metric-type/:metric-id/history" :metric-id #"[^/?#]+" :tags #"[^/?#]+"] [log-id metric-type metric-id tags]
    (if-let [metric (metric-types (extract metric-type))]
      (if-let [result (read-history metric log-id metric-id (extract-tags tags))]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )

  (route/not-found "")

  )

(defonce nimrod-app
  (handler/api (wrap-errors nimrod-routes))
  )