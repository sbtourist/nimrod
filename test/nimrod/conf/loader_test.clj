(ns nimrod.conf.loader-test
 (:use
   [clojure.test]
   [nimrod.conf.loader]
   [nimrod.log.tailer]
   )
 )

(deftest load-properties
  (testing "Load properties"
    (let [log (atom #{})]
      (binding [start-tailer #(swap! log conj (str %1 ":" %2))]
        (load-props "nimrod.properties")
        (is (contains? @log "log1:1"))
        (is (contains? @log "log2:2"))
        )
      )
    )
  )
