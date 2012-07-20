(ns nimrod.log.tailer-test
 (:use
   [clojure.test]
   [nimrod.log.tailer]))

(deftest start-tailer-with-duplicated-id
  (dosync (alter tailers assoc "1" {:log "log.txt" :tailer nil}))
  (try (start-tailer "1" "log2.txt" 10 true) (throw (RuntimeException. "Shouldn't be here!")) (catch IllegalStateException ex)))

(deftest tailers-list
  (dosync (alter tailers assoc "1" {:log "log.txt" :tailer nil}))
  (let [tailers (list-tailers)]
    (is (= "log.txt" (tailers "1")))))
