(ns nimrod.web.server
 (:use
   [clojure.tools.logging :as log]
   [ring.adapter.jetty]
   [nimrod.core.util]
   [nimrod.conf.loader]
   [nimrod.web.app])
 )

(defonce server (new-agent nil))

(defn start [jetty port]
  (when (nil? jetty)
    (try (load-props "nimrod.properties") (catch java.io.FileNotFoundException ex (log/info (.getMessage ex))))
    (run-jetty nimrod-app {:port port :join? false})
    )
  )

(defn stop [jetty]
  (when (not (nil? jetty)) (.stop jetty) nil)
  )