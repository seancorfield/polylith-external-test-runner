(ns org.corfield.external-test-runner.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [is]]
            [clojure.tools.deps.edn :as deps]
            [polylith.clj.core.test-runner-contract.interface :as test-runner-contract]
            [polylith.clj.core.util.interface.color :as color]
            [polylith.clj.core.util.interface.str :as str-util])
  (:import (java.lang ProcessBuilder ProcessBuilder$Redirect)
           (java.util List)))

(def ^:private my-runner-ns "org.corfield.external-test-runner-cli.main")
(def ^:private colorizer-ns "org.corfield.util.interface.color")

 (defn- clj-namespace? [{:keys [file-path]}]
  (or (str/ends-with? file-path ".clj")
      (str/ends-with? file-path ".cljc")))

 (defn- cljs-namespace? [{:keys [file-path]}]
   (or (str/ends-with? file-path ".cljs")
       (str/ends-with? file-path ".cljc")))

(defn brick-test-namespaces [test-opts by-ext bricks test-brick-names]
  (let [nses-fn (fn [selectors]
                  (juxt :name
                        #(mapcat (fn [selector]
                                   (-> % :namespaces selector))
                                 selectors)))
        selectors (cond-> [:test]
                    (:include-src-dir test-opts)
                    (conj :src))
        brick-name->namespaces
        (into {} (map (nses-fn selectors)) bricks)]
    (into []
          (comp (mapcat brick-name->namespaces)
                (filter by-ext)
                (map :namespace))
          test-brick-names)))

(defn project-test-namespaces [test-opts by-ext project-name projects-to-test namespaces]
  (when (contains? (set projects-to-test) project-name)
    (let [nses (cond-> (:test namespaces)
                 (:include-src-dir test-opts)
                 (into (:src namespaces)))]
      (into []
            (comp (filter by-ext)
                  (map :namespace))
            nses))))

;; see https://shadow-cljs.github.io/docs/UsersGuide.html#build-target-defaults
;; for :build-defaults and :target-defaults
(defn merge-shadow-defaults
  "Merge :build-defaults and :target-defaults into each build in :builds.
  Precedence (last wins): build-defaults < target-defaults for build's :target < build-specific."
  [{:keys [build-defaults target-defaults builds] :as shadow-edn}]
  (if (or (seq build-defaults) (seq target-defaults))
    (assoc shadow-edn :builds
           (into {}
                 (map (fn [[k build]]
                        [k (merge build-defaults
                                  (get target-defaults (:target build))
                                  build)]))
                 builds))
    shadow-edn))

(defn read-shadow-cljs [project-name projects-to-test dir]
  (when (and (contains? (set projects-to-test) project-name)
             (.exists (io/file dir "shadow-cljs.edn")))
    (-> (io/file dir "shadow-cljs.edn")
        (slurp)
        (edn/read-string)
        (merge-shadow-defaults))))

(defn components-msg [component-names color-mode]
  (when (seq component-names)
    [(color/component (str/join ", " component-names) color-mode)]))

(defn bases-msg
  ;; this is a test function in the src directory:
  {:test (fn [] (is (= nil (bases-msg [] nil))))}
  [base-names color-mode]
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
  (let [edn-fn (juxt :root :project)]
    (-> (deps/create-edn-maps)
        (edn-fn)
        (deps/merge-edns)
        :aliases)))

(defn- chase-opts-key
  "Given an aliases set and a keyword k, return a flattened vector of
  options for that k, resolving recursively if needed, or nil.
  Options can be a vector, or a hash map containing :jvm-opts identifying
  a vector, of JVM options."
  [aliases k]
  (let [opts-coll (get aliases k)
        ;; per issue #11, unroll :jvm-opts if needed:
        opts-coll (or (:jvm-opts opts-coll) opts-coll)]
    (when (seq opts-coll)
      (into [] (mapcat #(if (string? %) [%] (chase-opts-key aliases %))) opts-coll))))

(comment
  (let [options {:include-src-dir true}]
    (str "-Dorg.corfield.external-test-runner.opts='"
         (-> (pr-str (or options {}))
             (str/replace "'" "''"))
         "'"))
  )

(defn- run-cmd [project-dir cmd & [failure-msg failure-data]]
  (let [pb (doto (ProcessBuilder. ^List cmd)
             (.redirectOutput ProcessBuilder$Redirect/INHERIT)
             (.redirectError  ProcessBuilder$Redirect/INHERIT))]
    (when project-dir
      (.directory pb (io/file project-dir)))
    (when-not (-> pb (.start) (.waitFor) (zero?))
      (throw (ex-info (or failure-msg "Command failed")
                      (merge (when project-dir
                               {:project-dir project-dir})
                             (or failure-data {:cmd cmd})))))))

(defn- java-test-runner
  [all-paths setup-fn teardown-fn process-ns color-mode
   project-name test-nses* options-as-jvm java-opts]
  (let [path-sep  (System/getProperty "path.separator")
        classpath (str/join path-sep
                            (->> all-paths
                                 (cons (ns->src colorizer-ns))
                                 (cons (ns->src process-ns))))
        test-args (cond-> [color-mode project-name]
                    setup-fn
                    (conj (str setup-fn))
                    :always
                    (into (deref test-nses*))
                    teardown-fn
                    (conj (str teardown-fn)))
        java-cmd  (-> (cond-> [(find-java)]
                        java-opts
                        (into java-opts)
                        (seq options-as-jvm)
                        (into options-as-jvm))
                      (into ["-cp" classpath
                             "clojure.main" "-m" process-ns])
                      (into test-args))]
    (run-cmd nil java-cmd "External test runner failed" {:process-ns process-ns})))

(defn- olical-test-runner
  [all-paths test-nses* java-opts opts]
  (let [path-sep  (System/getProperty "path.separator")
        classpath (str/join path-sep (cons (ns->src colorizer-ns) all-paths))
        src-test  (filter #(let [f (io/file %)]
                             (and (.exists f) (.isDirectory f))) all-paths)
        {:keys [var include exclude]} (:focus opts)
        test-args (-> []
                      (into (mapcat #(vector "-d" (str %))) src-test)
                      (into (mapcat #(vector "-n" (str %))) (deref test-nses*))
                      (cond->
                       (seq var)
                       (into (mapcat #(vector "-v" (str %))) var)
                       (seq include)
                       (into (mapcat #(vector "-i" (str %))) include)
                       (seq exclude)
                       (into (mapcat #(vector "-e" (str %))) exclude)))
        java-cmd  (-> (cond-> [(find-java)]
                        java-opts
                        (into java-opts))
                      (into ["-cp" classpath
                             "clojure.main" "-m" "cljs-test-runner.main"])
                      (into test-args))]
    (run-cmd nil java-cmd "cljs-test-runner failed" {})))

(defn- shadow-compile
  [project-dir build]
  (run-cmd project-dir
           ["npx" "shadow-cljs"
            ;; maybe aliases go here?
            "compile" (name build)]
           "Shadow-cljs compilation failed"
           {:build build}))

(defmulti shadow-test (fn [_ {:keys [target]} _] target))

(defmethod shadow-test :node-test
  [project-dir {:keys [output-to autorun]} build]
  (shadow-compile project-dir build)
  (when-not autorun
    (run-cmd project-dir
             ["node" output-to]
             "Shadow-cljs node test failed"
             {:output-to output-to})))

(defmethod shadow-test :karma
  [project-dir {:keys [output-to]} build]
  (shadow-compile project-dir build)
  (run-cmd project-dir
           ["npx" "karma" "start" "--single-run"]
           "Shadow-cljs Karma test failed"
           {:output-to output-to}))

(defmethod shadow-test :default
  [_ {:keys [target]} build]
  (throw (ex-info (str "Unsupported Shadow-cljs test target: " target
                       " in selected build: " build)
                  {:target target :build build})))

(defn- cljs-test-runner
  [all-paths setup-fn teardown-fn project-dir test-cljs* shadow* java-opts test-opts]
  (when setup-fn
    (println "\nsetup-fn not supported for ClojureScript tests, ignoring" setup-fn))
  (when teardown-fn
    (println "\nteardown-fn not supported for ClojureScript tests, ignoring" teardown-fn))
  (cond
    @shadow*
    (let [build-key (-> test-opts :shadow-build (or :test))
          build-map (-> @shadow* :builds build-key)]
      (if (:target build-map)
        (shadow-test project-dir build-map build-key)
        (throw (ex-info (str "Unable to determine Shadow-cljs build or target in: " project-dir)
                        {:builds  (-> @shadow* :builds (keys))
                         :targets (->> @shadow* :builds (vals) (map :target))}))))

    ;; if no runner is specified, we still check for it on the classpath:
    (and (contains? #{nil :olical} (:cljs-test-runner test-opts))
         (seq (filter #(re-find #"olical" %) all-paths)))
    (olical-test-runner all-paths test-cljs* java-opts test-opts)

    (contains? #{:shadow :shadow-cljs} (:cljs-test-runner test-opts))
    (throw (ex-info "Shadow-cljs test runner specified but no shadow-cljs.edn file found in project directory."
                    {:project-dir project-dir}))

    (contains? #{:olical} (:cljs-test-runner test-opts))
    (throw (ex-info "cljs-test-runner specified but not found on classpath."
                    {:project-dir project-dir}))

    :else
    (println "\nIgnoring" (count @test-cljs*) "cljc/cljs test namespace(s) — no supported ClojureScript test runner found.")))

(defn create
  [{:keys [workspace project test-settings] :as _all}]
  (let [env-opts  (-> (System/getenv "ORG_CORFIELD_EXTERNAL_TEST_RUNNER")
                      (or "{}")
                      (edn/read-string))
        ws-opts   (-> workspace :settings :test)
        test-opts (merge (:org.corfield/external-test-runner test-settings)
                         (:org.corfield/external-test-runner ws-opts)
                         env-opts)
        _
        (when (seq test-opts)
          (println "Test runner options:")
          (doseq [[k v] test-opts]
            (println " " k "=>" v))
          (println ""))
        {:keys [bases components]} workspace
        {:keys [namespaces paths project-dir projects-to-test]
         project-name :name} project
        bricks-to-test (if (:include-src-dir test-opts)
                         (or (:bricks-to-test-all-sources project)
                             (:bricks-to-test project))
                         (:bricks-to-test project))
        ;; if no runner is specified, we will check for shadow-clj usage:
        shadow?        (contains? #{nil :shadow :shadow-cljs}
                                  (:cljs-test-runner test-opts))

        ;; TODO: if the project tests aren't to be run, we might further narrow this down
        test-sources-present (-> paths :test seq)
        test-nses*     (->> [(brick-test-namespaces test-opts clj-namespace? (into components bases) bricks-to-test)
                             (project-test-namespaces test-opts clj-namespace? project-name projects-to-test namespaces)]
                            (into [] cat)
                            (delay))
        shadow*        (delay (when shadow?
                                (read-shadow-cljs project-name projects-to-test project-dir)))
        test-cljs*     (->> [(brick-test-namespaces test-opts cljs-namespace? (into components bases) bricks-to-test)
                             (project-test-namespaces test-opts cljs-namespace? project-name projects-to-test namespaces)]
                            (into [] cat)
                            (delay))
        java-opts      (or (System/getenv "POLY_TEST_JVM_OPTS")
                           (System/getProperty "poly.test.jvm.opts"))
        opt-key        (when (and java-opts (re-find #"^:[-a-zA-Z0-9]+$" java-opts))
                         (keyword (subs java-opts 1)))
        java-opts      (if opt-key
                         (into [] (remove nil?) (chase-opts-key (get-project-aliases) opt-key))
                         (when java-opts (str/split java-opts #" ")))
        ;; turn the options hash into a vector of JVM options that the CLI
        ;; test runner can recognize:
        options-as-jvm (cond-> []
                         (seq test-opts)
                         (conj (str "-Dorg.corfield.external-test-runner.opts="
                                    (pr-str test-opts))))]

    (reify test-runner-contract/TestRunner
      (test-runner-name [_] "Polylith org.corfield.external-test-runner")

      (test-sources-present? [_] test-sources-present)

      (tests-present? [this {_eval-in-project :eval-in-project :as _opts}]
        (and (test-runner-contract/test-sources-present? this)
             (or (seq @test-nses*)
                 (seq @test-cljs*))))

      (run-tests [this {:keys [all-paths setup-fn teardown-fn process-ns color-mode] :as opts}]
        (when (test-runner-contract/tests-present? this opts)
          (let [run-message (run-message project-name components bases bricks-to-test
                                         projects-to-test color-mode)
                maybe-cljs? (not (contains? #{:none :ignore} (:cljs-test-runner test-opts)))]
            (println run-message)
            (when (seq @test-nses*)
              (when (and maybe-cljs? (seq @test-cljs*))
                (println "\nRunning Clojure tests..."))
              (java-test-runner all-paths setup-fn teardown-fn process-ns color-mode
                                project-name test-nses* options-as-jvm java-opts))
            (when (and maybe-cljs? (seq @test-cljs*))
              (when (seq @test-nses*)
                (println "\nRunning ClojureScript tests..."))
              (cljs-test-runner all-paths setup-fn teardown-fn project-dir
                                test-cljs* shadow* java-opts test-opts)))))
      test-runner-contract/ExternalTestRunner
      (external-process-namespace [_] my-runner-ns))))
