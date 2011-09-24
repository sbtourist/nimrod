(ns nimrod.conf.loader-test
 (:use
   [clojure.test]
   [nimrod.conf.loader]
   [nimrod.core.history]
   [nimrod.log.tailer]
   )
 )

(deftest load-properties
  (testing "Load logs"
    (let [log (atom #{})]
      (with-redefs [start-tailer #(swap! log conj (str %1 ":" %2))]
        (load-props "nimrod1.properties")
        (is (contains? @log "log1:1"))
        (is (contains? @log "log2:2"))
        )
      )
    )
  (testing "Load default history age"
    (let [age (atom nil)]
      (with-redefs [set-default-history-age #(reset! age %1)]
        (load-props "nimrod2.properties")
        (is (= 1 @age))
        )
      )
    )
  )
