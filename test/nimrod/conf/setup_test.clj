(ns nimrod.conf.setup-test
 (:use
   [clojure.test]
   [nimrod.conf.setup]
   [nimrod.core.store]
   [nimrod.log.tailer]))

(deftest setup-logs
    (let [log (atom #{})]
      (with-redefs [start-tailer #(swap! log conj (str %1 ":" %2 ":" %3 ":" %4))
                    new-disk-store (fn [_ _] nil)]
        (setup "nimrod1.conf")
        (is (contains? @log "1:log1:1:true"))
        (is (contains? @log "2:log2:2:false")))))

(deftest setup-disk-store
    (let [path (atom nil) options (atom nil)]
      (with-redefs [new-disk-store (fn [p o] (reset! path p) (reset! options o))]
        (setup "nimrod2.conf")
        (is (= "nimrod-data/db" @path))
        (is (= {"cache.entries" 1 "cache.results" 2} @options)))))