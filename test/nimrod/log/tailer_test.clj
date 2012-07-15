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

(deftest update-and-show-tailers-stats
    (update-tail-stats :log-timestamp :processed-logs-per-second)
    (update-tail-stats :log-timestamp :processed-logs-per-second)
    (update-tail-stats :log-timestamp :processed-logs-per-second)
    (update-tail-stats :metric-timestamp :processed-metrics-per-second)
    (update-tail-stats :metric-timestamp :processed-metrics-per-second)
    (testing "Processed logs per second in same second"
      (is (= 3 ((show-tail-stats) :processed-logs-per-second))))
    (testing "Processed metrics per second in same second"
      (is (= 2 ((show-tail-stats) :processed-metrics-per-second))))
    (with-redefs [nimrod.core.util/clock (fn [] (+ (System/currentTimeMillis) (nimrod.core.util/minutes 1)))]
      (testing "Processed logs is now reset"
        (is (= 0 ((show-tail-stats) :processed-logs-per-second))))
      (testing "Processed metrics is now reset"
        (is (= 0 ((show-tail-stats) :processed-metrics-per-second))))))
