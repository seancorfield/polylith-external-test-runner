(ns org.corfield.external-test-runner.interface-test
  (:require [clojure.test :refer [deftest is]]
            #?(:clj [org.corfield.external-test-runner.interface])))

(println #?(:cljs "\nCommon test running as ClojureScript."
            :clj  "\nCommon test running as Clojure."))

(deftest ^:dev dummy-test
  ;; I should run because I'm .cljc
  (is (= 1 1)))
