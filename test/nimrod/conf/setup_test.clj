(ns nimrod.conf.setup-test
 (:use
   [clojure.test]
   [nimrod.conf.setup]
   [nimrod.core.store]
   [nimrod.log.tailer]))

(deftest setup-logs
    (let [log (atom #{})]
      (with-redefs [start-tailer #(swap! log conj (str %1 ":" %2))]
        (setup "nimrod1.properties")
        (is (contains? @log "log1:1"))
        (is (contains? @log "log2:2")))))

(deftest setup-memory-store
    (let [setup? (atom false)]
      (with-redefs [new-memory-store (fn [] (reset! setup? true))]
        (setup "nimrod2.properties")
        (is @setup?))))

(deftest setup-disk-store
    (let [path (atom "")]
      (with-redefs [new-disk-store (fn [p] (reset! path p))]
        (setup "nimrod3.properties")
        (is (= "nimrod-data/db" @path)))))