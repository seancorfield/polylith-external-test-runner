(ns org.corfield.external-test-runner.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.deps.alpha :as deps]
            [polylith.clj.core.test-runner-contract.interface :as test-runner-contract]
            [polylith.clj.core.util.interface.color :as color]
            [polylith.clj.core.util.interface.str :as str-util])
  (:import (java.lang ProcessBuilder ProcessBuilder$Redirect)
           (java.util List)))

(defn brick-test-namespaces [bricks test-brick-names]
  (let [brick-name->namespaces (into {} (map (juxt :name #(-> % :namespaces :test))) bricks)]
    (into []
          (comp (mapcat brick-name->namespaces)
                (map :namespace))
          test-brick-names)))

(defn project-test-namespaces [project-name projects-to-test namespaces]
  (when (contains? (set projects-to-test) project-name)
    (mapv :namespace (:test namespaces))))

(defn components-msg [component-names color-mode]
  (when (seq component-names)
    [(color/component (str/join ", " component-names) color-mode)]))

(defn bases-msg [base-names color-mode]
  (when (seq base-names)
    [(color/base (str/join ", " base-names) color-mode)]))

(defn run-message [project-name components bases bricks-to-test projects-to-test color-mode]
  (let [component-names (into #{} (map :name) components)
        base-names (into #{} (map :name) bases)
        bases-to-test (filterv #(contains? base-names %) bricks-to-test)
        bases-to-test-msg (bases-msg bases-to-test color-mode)
        components-to-test (filterv #(contains? component-names %) bricks-to-test)
        components-to-test-msg (components-msg components-to-test color-mode)
        projects-to-test-msg (when (seq projects-to-test)
                               [(color/project (str/join ", " projects-to-test) color-mode)])
        entities-msg (str/join ", " (into [] cat [components-to-test-msg
                                                  bases-to-test-msg
                                                  projects-to-test-msg]))
        project-cnt (count projects-to-test)
        bricks-cnt (count bricks-to-test)
        project-msg (if (zero? project-cnt)
                      ""
                      (str " and " (str-util/count-things "project" project-cnt)))]
    (str "Running tests from the " (color/project project-name color-mode) " project, including "
         (str-util/count-things "brick" bricks-cnt) project-msg ": " entities-msg)))

(defn ns->src [ns-str]
  (let [file-sep (System/getProperty "file.separator")
        ns-path  (-> ns-str
                     (str/replace "." file-sep)
                     (str/replace "-" "_")
                     (str ".clj"))]
    (-> ns-path
        (io/resource)
        (.getFile)
        (str/replace (str file-sep ns-path) ""))))

(defn- find-java []
  (let [java-cmd (or (System/getenv "JAVA_CMD")
                     (when-let [home (System/getenv "JAVA_HOME")]
                       (str home "/bin/java")))]
    (if (and java-cmd (.exists (io/file java-cmd)))
      java-cmd
      "java")))

(defn- get-project-aliases []
  (let [edn-fn (juxt :root-edn :project-edn)]
    (-> (deps/find-edn-maps)
        (edn-fn)
        (deps/merge-edns)
        :aliases)))

(defn- chase-opts-key
  "Given an aliases set and a keyword k, return a flattened vector of
  options for that k, resolving recursively if needed, or nil."
  [aliases k]
  (let [opts-coll (get aliases k)]
    (when (seq opts-coll)
      (into [] (mapcat #(if (string? %) [%] (chase-opts-key aliases %))) opts-coll))))

(defn create
  [{:keys [workspace project changes #_test-settings]}]
  (let [{:keys [bases components]} workspace
        {:keys [name namespaces paths]} project
        {:keys [project-to-bricks-to-test project-to-projects-to-test]} changes

        ;; TODO: if the project tests aren't to be run, we might further narrow this down
        test-sources-present* (delay (-> paths :test seq))
        bricks-to-test* (delay (project-to-bricks-to-test name))
        projects-to-test* (delay (project-to-projects-to-test name))
        test-nses*     (->> [(brick-test-namespaces (into components bases) @bricks-to-test*)
                             (project-test-namespaces name @projects-to-test* namespaces)]
                            (into [] cat)
                            (delay))
        path-sep       (System/getProperty "path.separator")
        my-runner-ns   "org.corfield.external-test-runner-cli.main"
        colorizer-ns   "org.corfield.util.interface.color"
        java-opts      (or (System/getenv "POLY_TEST_JVM_OPTS")
                           (System/getProperty "poly.test.jvm.opts"))
        opt-key        (when (and java-opts (re-find #"^:[-a-zA-Z0-9]+$" java-opts))
                         (keyword (subs java-opts 1)))
        java-opts      (if opt-key
                         (into [] (remove nil?) (chase-opts-key (get-project-aliases) opt-key))
                         (str/split java-opts #" "))]

    (reify test-runner-contract/TestRunner
      (test-runner-name [_] "Polylith org.corfield.external-test-runner")

      (test-sources-present? [_] @test-sources-present*)

      (tests-present? [this {_eval-in-project :eval-in-project :as _opts}]
        (and (test-runner-contract/test-sources-present? this)
             (seq @test-nses*)))

      (run-tests [this {:keys [all-paths setup-fn teardown-fn process-ns color-mode] :as opts}]
                 (when (test-runner-contract/tests-present? this opts)
                   (let [run-message (run-message name components bases @bricks-to-test*
                                                  @projects-to-test* color-mode)
                         _         (println run-message)
                         classpath (str/join path-sep
                                             (->> all-paths
                                                  (cons (ns->src colorizer-ns))
                                                  (cons (ns->src my-runner-ns))))
                         test-args (cond-> [color-mode name]
                                     setup-fn
                                     (conj (str setup-fn))
                                     :always
                                     (into (deref test-nses*))
                                     teardown-fn
                                     (conj (str teardown-fn)))
                         java-cmd  (-> (cond-> [(find-java)]
                                         java-opts
                                         (into java-opts))
                                       (into ["-cp" classpath
                                              "clojure.main" "-m" process-ns])
                                       (into test-args))
                         pb        (doto (ProcessBuilder. ^List java-cmd)
                                     (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                                     (.redirectError  ProcessBuilder$Redirect/INHERIT))]
                     (when-not (-> pb (.start) (.waitFor) (zero?))
                       (throw (ex-info "External test runner failed" {:process-ns process-ns}))))))
      test-runner-contract/ExternalTestRunner
      (external-process-namespace [_] my-runner-ns))))
