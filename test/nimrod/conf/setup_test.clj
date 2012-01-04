(ns nimrod.conf.setup-test
 (:use
   [clojure.test]
   [nimrod.conf.setup]
   [nimrod.core.store]
   [nimrod.log.tailer]))

(deftest setup-logs
    (let [log (atom #{})]
      (with-redefs [start-tailer #(swap! log conj (str %1 ":" %2 ":" %3))]
        (setup "nimrod1.conf")
        (is (contains? @log "1:log1:1"))
        (is (contains? @log "2:log2:2")))))

(deftest setup-memory-store
    (let [setup? (atom false)]
      (with-redefs [new-memory-store (fn [] (reset! setup? true))]
        (setup "nimrod2.conf")
        (is @setup?))))

(deftest setup-disk-store
    (let [path (atom "")]
      (with-redefs [new-disk-store (fn [p] (reset! path p))]
        (setup "nimrod3.conf")
        (is (= "nimrod-data/db" @path)))))