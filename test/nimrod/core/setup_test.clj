(ns nimrod.core.setup-test
 (:use
   [clojure.test]
   [nimrod.core.setup]
   [nimrod.core.store]
   [nimrod.log.tailer]
   [nimrod.web.server]))

(deftest setup-logs
  (let [log (atom #{})]
    (with-redefs [start-tailer #(swap! log conj (str %1 ":" %2 ":" %3 ":" %4))
                  new-disk-store (fn [_ _ _] nil)]
      (setup "test/nimrod1.conf")
      (is (contains? @log "1:log1:1:true"))
      (is (contains? @log "2:log2:2:false")))))

(deftest setup-disk-store
  (let [path (atom nil) options (atom nil) sampling (atom nil)]
    (with-redefs [new-disk-store (fn [p o s] (reset! path p) (reset! options o) (reset! sampling s))]
      (setup "test/nimrod2.conf")
      (is (= "/opt/nimrod/nimrod-data/db" @path))
      (is (= {"cache.entries" 1 "cache.results" 2 "batch.op-limit" 3 "batch.queue-limit" 4 "defrag.op-limit" 5} @options))
      (is (= {"test.frequency" 10} @sampling)))))

(deftest setup-server
  (let [data (atom nil)]
    (with-redefs 
      [start-server #(reset! data (str %1 ":" %2))
      max-busy-requests (atom nil)
      new-disk-store (fn [_ _ _] nil)]
      (setup "test/nimrod3.conf")
      (is (= "8080:10" @data))
      (is (= 1 @max-busy-requests)))))
