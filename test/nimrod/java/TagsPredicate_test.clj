(ns nimrod.java.TagsPredicate-test
  (:use [clojure.test] 
        [cheshire.core])
  (:import [nimrod.java TagsPredicate]))

(deftest check-tags
  (testing "Tags are contained"
    (is (= true (TagsPredicate/contains (generate-smile {"ignored" true "tags" ["tag1" "tag2"]}) "tag1,tag2"))))
  (testing "Tags are not  contained"
    (is (= false (TagsPredicate/contains (generate-smile {"ignored" true "tags" ["tag1" "tag2"]}) "tag1,tag2,tag3")))))
