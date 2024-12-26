(ns org.corfield.external-test-runner.ignored-test
  (:require [clojure.test :refer [deftest is]]
            [org.corfield.external-test-runner.core]))

(deftest dummy-test
  ;; I should not run because I'm ClojureScript
  (is (= 1 0)))
