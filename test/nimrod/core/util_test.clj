(ns nimrod.core.util-test
  (:use 
    [clojure.test]
    [nimrod.core.util])
  (:import [java.util Date]))

(deftest age-parsing
  (testing "Age parsing with millis"
    (is (= (minutes 1) (age-to-millis (str (minutes 1))))))
  (testing "Age parsing with string"
    (is (= (minutes 1) (age-to-millis "1m")))))

(deftest time-parsing
  (testing "Time parsing with millis"
    (let [now (System/currentTimeMillis)] (is (= (- now (minutes 1)) (time-to-millis now (str (- now (minutes 1))))))))
  (testing "Time parsing with string"
    (let [now (System/currentTimeMillis)] (is (= (- now (minutes 1)) (time-to-millis now "1m.ago"))))))
