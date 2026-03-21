(ns org.corfield.external-test-runner.ignored-test
  (:require [clojure.test :refer [deftest is]]))

(println "\nThis is a ClojureScript test file.")

(deftest dummy-test
  (is (= 1 1)))
