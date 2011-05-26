(ns nimrod.web.shutdown
 (:gen-class)
 (:use 
   [nimrod.web.server])
 )

(defn -main []
  (send-off server stop)
  )