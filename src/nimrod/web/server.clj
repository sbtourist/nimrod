(ns nimrod.web.server
 (:use
   [ring.adapter.jetty]
   [nimrod.web.app]))

(defn start-server [port threads]
  (run-jetty nimrod-app {:port port :max-threads threads :join? false}))