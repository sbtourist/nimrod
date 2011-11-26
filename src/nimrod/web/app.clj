(ns nimrod.web.app
 (:use
   [clojure.string :as string :only [split]]
   [clojure.tools.logging :as log]
   [cheshire.core :as json]
   [compojure.core :as http]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [ring.util.response :as response]
   [nimrod.core.metric]
   [nimrod.core.store]
   [nimrod.log.tailer])
 (:refer-clojure :exclude [split]))

(defonce response-codes {:ok 200 :no-content 204 :not-found 404 :error 500})
(defonce std-response-headers {"Content-Type" "application/json"})
(defonce cors-response-headers {"Content-Type" "application/json" "Access-Control-Allow-Origin" "*"})

(defn- type-of [metric]
  (condp = metric
    "alerts" alert
    "gauges" gauge
    "counters" counter
    "timers" timer
    nil))

(defn- convert [age]
  (when (not (nil? age)) (Long/parseLong age)))

(defn- extract [tags]
  (when (seq tags) (into #{} (string/split tags #","))))

(defn- drop-last-char [s]
  (apply str (drop-last (seq s))))

(defn- std-response
  ([status]
    (std-response status std-response-headers nil))
  ([status body]
    (std-response status std-response-headers body))
  ([status headers body]
    {:headers headers :status (response-codes status) :body (if body (json/generate-string body) nil)}))

(defn- cors-response 
  ([status]
    (std-response status cors-response-headers nil))
  ([status body]
    (std-response status cors-response-headers body)))

(defn- redirect-response [url]
  (response/redirect url))

(defn- wrap-errors [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception ex
        (log/error (.getMessage ex) ex)
        (std-response :error {:error (.getMessage ex)})))))

(http/defroutes nimrod-routes
  
  (http/POST "/logs" [file interval]
    (let [tailer (start-tailer file (Long/parseLong (or interval "1000")))]
      (std-response :ok {tailer file})))
  (http/GET "/logs" []
    (cors-response :ok (list-tailers)))
  (http/GET "/logs/" [:as request]
    (redirect-response (drop-last-char (request :uri))))
  (http/DELETE "/logs/:log-id" [log-id]
    (stop-tailer log-id)
    (std-response :no-content))
  
  (http/GET ["/logs/:log-id/:metric-type"] [log-id metric-type]
    (if-let [metric (type-of metric-type)]
      (if-let [result (list-metrics @metric-agent log-id (name-of metric))]
        (cors-response :ok result)
        (cors-response :not-found))
      (cors-response :error {:error (str "Bad metric type: " metric-type)})))
  (http/GET ["/logs/:log-id/:metric-type/"] [log-id metric-type :as request]
    (redirect-response (drop-last-char (request :uri))))
  
  (http/GET ["/logs/:log-id/:metric-type/:metric-id" :metric-id #"[^/?#]+"] [log-id metric-type metric-id]
    (if-let [metric (type-of metric-type)]
      (if-let [result (read-metric @metric-agent log-id (name-of metric) metric-id)]
        (cors-response :ok result)
        (cors-response :not-found))
      (cors-response :error {:error (str "Bad metric type: " metric-type)})))
  (http/DELETE ["/logs/:log-id/:metric-type/:metric-id"] [log-id metric-type metric-id]
    (if-let [metric (type-of metric-type)]
      (do 
        (remove-metric @metric-agent log-id (name-of metric) metric-id)
        (std-response :no-content))
      (std-response :error {:error (str "Bad metric type: " metric-type)})))
  
  
  (http/GET ["/logs/:log-id/:metric-type/:metric-id/history" :metric-id #"[^/?#]+" :age #"\d+" :tags #"[^/?#]+"] [log-id metric-type metric-id age tags]
    (if-let [metric (type-of metric-type)]
      (if-let [result (read-metrics @metric-agent log-id (name-of metric) metric-id (or (convert age) Long/MAX_VALUE) (or (extract tags) #{}))]
        (cors-response :ok {:size (count result) :values result})
        (cors-response :not-found))
      (cors-response :error {:error (str "Bad metric type: " metric-type)})))
  (http/DELETE ["/logs/:log-id/:metric-type/:metric-id/history" :age #"\d+"] [log-id metric-type metric-id age]
    (if-let [metric (type-of metric-type)]
      (do
        (remove-metrics @metric-agent log-id (name-of metric) metric-id (convert age))
        (std-response :no-content))
      (std-response :error {:error (str "Bad metric type: " metric-type)})))
  
  (route/not-found ""))

(defonce nimrod-app
  (handler/api (wrap-errors nimrod-routes)))