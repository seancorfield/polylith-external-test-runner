(ns org.corfield.external-test-runner-cli.main
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
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

(defn- filter-vars!
  "Copied from https://github.com/cognitect-labs/test-runner/blob/7284cda41fb9edc0f3bc6b6185cfb7138fc8a023/src/cognitect/test_runner.clj#L37
   and adjusted for a single namespace.
   `clojure.test`-only for now."
  [ns filter-fn]
  (doseq [[_name var] (ns-publics ns)]
    (when (:test (meta var))
      (when (not (filter-fn var))
        (alter-meta! var #(-> %
                              (assoc ::test (:test %))
                              (dissoc :test)))))))

(defn- var-filter
  "Copied from https://github.com/cognitect-labs/test-runner/blob/7284cda41fb9edc0f3bc6b6185cfb7138fc8a023/src/cognitect/test_runner.clj#L19C1-L35C32"
  [{:keys [var include exclude]}]
  (let [test-specific (if var
                        #((set (keep resolve var)) %)
                        (constantly true))
        test-inclusion (if include
                         #((apply some-fn include) (meta %))
                         (constantly true))
        test-exclusion (if exclude
                         #((complement (apply some-fn exclude)) (meta %))
                         (constantly true))]
    #(and (test-specific %)
          (test-inclusion %)
          (test-exclusion %))))

(defn- restore-vars!
  "Copied from https://github.com/cognitect-labs/test-runner/blob/7284cda41fb9edc0f3bc6b6185cfb7138fc8a023/src/cognitect/test_runner.clj#L47
   and adjusted for a single namespace.
   `clojure.test`-only for now."
  [ns]
  (doseq [[_name var] (ns-publics ns)]
    (when (::test (meta var))
      (alter-meta! var #(-> %
                            (assoc :test (::test %))
                            (dissoc ::test))))))

(defn- contains-tests?
  "Check if a namespace contains some tests to be executed.
  The predicate determines how to identify a test."
  [ns pred]
  (some pred (-> ns ns-publics vals)))

(defn -main [& args]
  (when-not (<= 3 (count args))
    (println "Requires the color-mode, the project name, and at least one namespace to test")
    (System/exit 1))

  (let [options (-> (System/getProperty "org.corfield.external-test-runner.opts")
                    (or "{}")
                    (edn/read-string))
        filter-fn (var-filter (:focus options))
        [color-mode & args] args
        [project-name & args] args
        [setup-fn & nses]
        (if (str/includes? (first args) "/")
          args
          (cons nil args))
        [teardown-fn nses]
        (let [poss-teardown (last nses)]
          (if (and poss-teardown (str/includes? poss-teardown "/"))
            [poss-teardown (butlast nses)]
            [nil nses]))
        lazy-run
        (try (requiring-resolve 'lazytest.repl/run-tests)
             (catch Exception _ nil))
        is-test? ; is var a clojure.test test?
        (fn [v] (-> v (meta) :test))
        lazy-is-test? ; is var a lazytest test?
        (try (requiring-resolve 'lazytest.find/find-test-var)
             (catch Exception _ nil))
        merge-summaries
        (fn [sum1 sum2]
          (dissoc (merge-with + sum1 sum2) :skip))]
    (if (execute-fn setup-fn "setup" project-name color-mode)
      (try
        (doseq [test-ns nses]
          (let [test-sym (symbol test-ns)
                {:keys [error fail pass skip]}
                (try
                  (require test-sym)
                  (filter-vars! test-sym filter-fn)
                  ;; assume no tests in ns (test-sym), i.e., skip the ns --
                  ;; if we find tests, we'll run the appropriate test runner,
                  ;; merge those results into the summary and remove :skip
                  (cond-> {:error 0 :fail 0 :pass 0 :skip true}
                    (contains-tests? test-sym is-test?)
                    (merge-summaries (test/run-tests test-sym))
                    (and lazy-run lazy-is-test?
                         (contains-tests? test-sym lazy-is-test?))
                    (merge-summaries (lazy-run test-sym)))
                  (catch Exception e
                    (.printStackTrace e)
                    (println (str (color/error color-mode "Couldn't run test statement")
                                  " for the " (color/project project-name color-mode)
                                  " project: " test-ns " " (color/error color-mode e))))
                  (finally
                    (restore-vars! test-sym)))
                result-str (str "Test results: " pass " passes, " fail " failures, " error " errors.")]
            (when-not skip
              (when (or (nil? error)
                        (< 0 error)
                        (< 0 fail))
                (throw (Exception. (str "\n" (color/error color-mode result-str))))))
            (if skip
              (println (str "\nNo "
                            (when (seq (:focus options)) "applicable ")
                            "tests found in " test-ns))
              (println (str "\n" (color/ok color-mode result-str))))))
        (finally
          (when-not (execute-fn teardown-fn "teardown" project-name color-mode)
            (throw (ex-info "Test terminated due to teardown failure"
                            {:project project-name})))))
      (throw (ex-info (str "Test terminated due to setup failure")
                      {:project project-name})))
    (shutdown-agents)
    (System/exit 0)))
