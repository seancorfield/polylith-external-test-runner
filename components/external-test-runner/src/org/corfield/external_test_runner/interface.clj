(ns org.corfield.external-test-runner.interface
  (:require [org.corfield.external-test-runner.core :as core]))

(defn create [opts]
  (core/create opts))
