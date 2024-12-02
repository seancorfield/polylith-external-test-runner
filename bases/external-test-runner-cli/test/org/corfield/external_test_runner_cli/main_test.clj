(ns org.corfield.external-test-runner-cli.main-test
  (:require [clojure.test :refer [deftest is]]
            [org.corfield.external-test-runner-cli.main]))

(deftest ^:integration ^:dev jvm-opts-test
  ;; inside the tests, this option should not be set:
  (is (= nil (System/getProperty "poly.test.jvm.opts")))
  ;; but this option, set via a keyword, should be:
  (is (= "opts" (System/getProperty "example")))
  ;; and so should this, set recursively:
  (is (= "more.opts" (System/getProperty "sub.example")))
  ;; and issue #11 should set this too, recursively:
  (is (= "even.more.opts" (System/getProperty "nested.example"))))
