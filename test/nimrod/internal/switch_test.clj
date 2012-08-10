(ns nimrod.internal.switch-test
  (:use 
    [clojure.test]
    [nimrod.core.util]
    [nimrod.internal.switch]))

(defonce test-agent-1 (new-agent nil))
(defonce test-agent-2 (new-agent nil))

(deftest switch
  (let [interrupted (atom false)]
    (testing "Switch is activated"
      (send test-agent-1
        (fn [_] 
          (with-switch :requests 1
            (Thread/sleep 1000)
            (reset! interrupted true))))
      (Thread/sleep 250)
      (is (= false @interrupted)))
    (testing "Switch is interrupted"
      (send test-agent-2 
        (fn [_] 
          (with-switch :requests 1
            (Thread/sleep 1000)
            (reset! interrupted true))))
      (Thread/sleep 250)
      (is (= true @interrupted)))
    (testing "Switch is restored"
      (Thread/sleep 1000)
      (with-switch :requests 1
        (reset! interrupted false)
        (reset! interrupted true))
      (is (= false @interrupted)))))
