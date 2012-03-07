(ns nimrod.web.startup
 (:gen-class)
 (:use 
   [nimrod.web.server]))

(defn -main [port]
  (start (Long/parseLong port)))