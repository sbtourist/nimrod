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

(defn- type-of [metric-path]
  (condp = metric-path
    "alerts" (name-of alert)
    "gauges" (name-of gauge)
    "counters" (name-of counter)
    "timers" (name-of timer)
    nil))

(defn- path-of [metric-type]
  (condp = metric-type
    (name-of alert) "alerts"
    (name-of gauge) "gauges"
    (name-of counter) "counters"
    (name-of timer) "timers"
    nil))

(defn- parse-long [n]
  (when (not (nil? n)) (Long/parseLong n)))

(defn- extract-tags [value]
  (when (seq value) (into #{} (string/split value #","))))

(defn- extract-ints [value]
  (when (seq value) (into [] (for [s (string/split value #",")] (Integer/parseInt s)))))

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
  
  (http/POST ["/logs/:log-id/start" :log-id #"[^/?#]+"] [log-id file interval]
    (let [tailer (start-tailer log-id file (Long/parseLong (or interval "1000")))]
      (std-response :ok {tailer file})))
  (http/GET "/logs" []
    (cors-response :ok (list-tailers)))
  (http/GET "/logs/" [:as request]
    (redirect-response (drop-last-char (request :uri))))
  (http/GET "/logs/:log-id" [log-id]
    (std-response :ok (map path-of (list-types @metric-agent log-id))))
  (http/GET "/logs/:log-id/" [:as request]
    (redirect-response (drop-last-char (request :uri))))
  (http/POST "/logs/:log-id/stop" [log-id]
    (stop-tailer log-id)
    (std-response :no-content))
  
  (http/GET ["/logs/:log-id/:metric-type"] [log-id metric-type]
    (if-let [metric-type (type-of metric-type)]
      (if-let [result (list-metrics @metric-agent log-id metric-type)]
        (cors-response :ok result)
        (cors-response :not-found))
      (cors-response :error {:error (str "Bad metric type: " metric-type)})))
  (http/GET ["/logs/:log-id/:metric-type/"] [log-id metric-type :as request]
    (redirect-response (drop-last-char (request :uri))))
  
  (http/GET ["/logs/:log-id/:metric-type/:metric-id" :metric-id #"[^/?#]+"] [log-id metric-type metric-id]
    (if-let [metric-type (type-of metric-type)]
      (if-let [result (read-metric @metric-agent log-id metric-type metric-id)]
        (cors-response :ok result)
        (cors-response :not-found))
      (cors-response :error {:error (str "Bad metric type: " metric-type)})))
  (http/DELETE ["/logs/:log-id/:metric-type/:metric-id" :metric-id #"[^/?#]+"] [log-id metric-type metric-id]
    (if-let [metric-type (type-of metric-type)]
      (do 
        (remove-metric @metric-agent log-id metric-type metric-id)
        (std-response :no-content))
      (std-response :error {:error (str "Bad metric type: " metric-type)})))
  
  
  (http/GET ["/logs/:log-id/:metric-type/:metric-id/history" :metric-id #"[^/?#]+" :age #"\d+" :tags #"[^/?#]+" :limit #"\d+"] 
    [log-id metric-type metric-id age tags limit]
    (if-let [metric-type (type-of metric-type)]
      (if-let [result (read-history @metric-agent log-id metric-type metric-id (or (extract-tags tags) #{}) (parse-long age) (parse-long limit))]
        (cors-response :ok result)
        (cors-response :not-found))
      (cors-response :error {:error (str "Bad metric type: " metric-type)})))
  (http/GET ["/logs/:log-id/:metric-type/history/merge" :age #"\d+" :tags #"[^/?#]+" :limit #"\d+"] [log-id metric-type age tags limit]
    (if-let [metric-type (type-of metric-type)]
      (if-let [result (merge-history @metric-agent log-id metric-type (or (extract-tags tags) #{}) (parse-long age) (parse-long limit))]
        (cors-response :ok result)
        (cors-response :not-found))
      (std-response :error {:error (str "Bad metric type: " metric-type)})))
  (http/GET ["/logs/:log-id/:metric-type/:metric-id/history/aggregate" :metric-id #"[^/?#]+" :from #"\d+" :to #"\d+" :percentiles #"[\d|,]+"] 
    [log-id metric-type metric-id from to percentiles]
    (if-let [metric-type (type-of metric-type)]
      (if-let [result (aggregate-history @metric-agent log-id metric-type metric-id (parse-long from) (parse-long to) {:percentiles (sort (or (extract-ints percentiles) [25 50 75 99]))})]
        (cors-response :ok result)
        (cors-response :not-found))
      (cors-response :error {:error (str "Bad metric type: " metric-type)})))
  (http/POST ["/logs/:log-id/:metric-type/:metric-id/history/delete" :metric-id #"[^/?#]+" :age #"\d+"] [log-id metric-type metric-id age]
    (if-let [metric-type (type-of metric-type)]
      (do
        (remove-history @metric-agent log-id metric-type metric-id (or (parse-long age) Long/MAX_VALUE))
        (std-response :no-content))
      (std-response :error {:error (str "Bad metric type: " metric-type)})))
  (http/POST ["/logs/:log-id/:metric-type/history/delete" :age #"\d+"] [log-id metric-type age]
    (if-let [metric-type (type-of metric-type)]
      (do
        (remove-history @metric-agent log-id metric-type (or (parse-long age) Long/MAX_VALUE))
        (std-response :no-content))
      (std-response :error {:error (str "Bad metric type: " metric-type)})))
  
  (route/not-found ""))

(defonce nimrod-app
  (handler/api (wrap-errors nimrod-routes)))