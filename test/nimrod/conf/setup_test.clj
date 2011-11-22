(ns nimrod.conf.setup-test
 (:use
   [clojure.test]
   [nimrod.conf.setup]
   [nimrod.log.tailer]))

(deftest setup-with-properties
  (testing "Setup log tailers"
    (let [log (atom #{})]
      (with-redefs [start-tailer #(swap! log conj (str %1 ":" %2))]
        (setup "nimrod1.properties")
        (is (contains? @log "log1:1"))
        (is (contains? @log "log2:2"))))))
