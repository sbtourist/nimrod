(ns nimrod.web.startup
 (:gen-class)
 (:use 
   [nimrod.web.server])
 )

(defn -main [port]
  (send-off server start (Long/parseLong port))
  )