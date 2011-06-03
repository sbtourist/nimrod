(ns nimrod.web.server
 (:use 
   [ring.adapter.jetty]
   [nimrod.core.util]
   [nimrod.conf.loader]
   [nimrod.web.app])
 )

(defonce server (new-agent nil))

(defn start [jetty port]
  (when (nil? jetty)
    (load-props "nimrod.properties")
    (run-jetty nimrod-app {:port port :join? false})
    )
  )

(defn stop [jetty]
  (when (not (nil? jetty)) (.stop jetty) nil)
  )