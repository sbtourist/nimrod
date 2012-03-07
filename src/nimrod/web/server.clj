(ns nimrod.web.server
 (:use
   [clojure.tools.logging :as log]
   [ring.adapter.jetty]
   [nimrod.core.util]
   [nimrod.conf.setup]
   [nimrod.web.app]))

(defn start [port]
    (try (setup "nimrod.conf") (catch Exception ex (log/warn (.getMessage ex))))
    (run-jetty nimrod-app {:port port :join? false}))