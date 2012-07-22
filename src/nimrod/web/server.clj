(ns nimrod.web.server
 (:use
   [ring.adapter.jetty]
   [nimrod.web.app]))

(defn start-server [port]
  (run-jetty nimrod-app {:port port :join? false}))