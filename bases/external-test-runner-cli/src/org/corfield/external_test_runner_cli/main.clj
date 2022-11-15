(ns org.corfield.external-test-runner-cli.main
  (:require [clojure.string :as str]
            [clojure.test :as test]
            [org.corfield.util.interface.color :as color]))

(defn execute-fn [function fn-type project-name color-mode]
  (if function
    (do
      (println (str "Running test " fn-type " for the " (color/project project-name color-mode)
                    " project: " function))
      (try
        (if-let [fun (requiring-resolve (symbol function))]
          (fun project-name)
          (do
            (println (color/error color-mode (str "Could not find " fn-type " function: " function)))
            false))
        true
        (catch Throwable t
          (let [message (str (or (some-> t .getCause) (.getMessage t)))]
            (println (color/error color-mode (str "\nTest " fn-type " failed: " message)))
            false))))
    true))

(defn -main [& args]
  (when-not (<= 3 (count args))
    (println "Requires the color-mode, the project name, and at least one namespace to test")
    (System/exit 1))

  (let [[color-mode & args] args
        [project-name & args] args
        [setup-fn & nses]
        (if (str/includes? (first args) "/")
          args
          (cons nil args))
        [teardown-fn nses]
        (let [poss-teardown (last nses)]
          (if (and poss-teardown (str/includes? poss-teardown "/"))
            [poss-teardown (butlast nses)]
            [nil nses]))]
    (if (execute-fn setup-fn "setup" project-name color-mode)
      (try
        (doseq [test-ns nses]
          (let [test-sym (symbol test-ns)
                {:keys [error fail pass]}
                (try
                  (require test-sym)
                  (test/run-tests test-sym)
                  (catch Exception e
                    (.printStackTrace e)
                    (println (str (color/error color-mode "Couldn't run test statement")
                                  " for the " (color/project color-mode project-name)
                                  " project: " test-ns " " (color/error color-mode e)))))
                result-str (str "Test results: " pass " passes, " fail " failures, " error " errors.")]
            (when (or (nil? error)
                      (< 0 error)
                      (< 0 fail))
              (throw (Exception. (str "\n" (color/error color-mode result-str)))))
            (println (str "\n" (color/ok color-mode result-str)))))
        (finally
          (when-not (execute-fn teardown-fn "teardown" project-name color-mode)
            (throw (ex-info "Test terminated due to teardown failure"
                            {:project project-name})))))
      (throw (ex-info (str "Test terminated due to setup failure")
                      {:project project-name})))
    (shutdown-agents)
    (System/exit 0)))
