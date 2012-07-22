(ns nimrod.core.startup
 (:gen-class)
 (:require [clojure.tools.logging :as log])
 (:use [nimrod.core.setup]))

(defn -main []
  (try (setup "nimrod.conf") (catch Exception ex (log/error (.getMessage ex)))))