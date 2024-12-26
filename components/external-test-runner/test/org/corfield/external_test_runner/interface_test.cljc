(ns org.corfield.external-test-runner.interface-test
  (:require [clojure.test :refer [deftest is]]
            [org.corfield.external-test-runner.interface]))

(deftest ^:dev dummy-test
  ;; I should run because I'm .cljc
  (is (= 1 1)))
