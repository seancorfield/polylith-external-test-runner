(ns org.corfield.external-test-runner.core-test
  (:require [clojure.test :refer [deftest is]]
            [org.corfield.external-test-runner.core]))

(deftest dummy-test
  (is (= 1 1)))
