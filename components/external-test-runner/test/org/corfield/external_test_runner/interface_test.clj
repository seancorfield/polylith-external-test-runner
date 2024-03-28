(ns org.corfield.external-test-runner.interface-test
  (:require [clojure.test :refer [deftest is]]
            [org.corfield.external-test-runner.interface]))

(deftest ^:dev dummy-test
  (is (= 1 1)))
