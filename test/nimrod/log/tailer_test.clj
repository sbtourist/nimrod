(ns nimrod.log.tailer-test
 (:use
   [clojure.test]
   [nimrod.log.tailer]))

(deftest tailers-list
  (dosync (alter tailers assoc "1" {:log "log.txt" :tailer nil}))
  (let [tailers (list-tailers)]
    (is (= "log.txt" (tailers "1")))))