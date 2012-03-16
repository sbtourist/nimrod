(ns nimrod.conf.setup-test
 (:use
   [clojure.test]
   [nimrod.conf.setup]
   [nimrod.core.store]
   [nimrod.log.tailer]))

(deftest setup-logs
    (let [log (atom #{})]
      (with-redefs [start-tailer #(swap! log conj (str %1 ":" %2 ":" %3 ":" %4))]
        (setup "nimrod1.conf")
        (is (contains? @log "1:log1:1:true"))
        (is (contains? @log "2:log2:2:false")))))

(deftest setup-disk-store
    (let [path (atom "")]
      (with-redefs [new-disk-store (fn [p] (reset! path p))]
        (setup "nimrod2.conf")
        (is (= "nimrod-data/db" @path)))))