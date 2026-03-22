---
name: "clojure-polylith"
description: "Clojure Polylith external test runner with ProcessBuilder-based subprocess isolation, Shadow-cljs and cljs-test-runner ClojureScript support, and EDN configuration. USE FOR: polylith bricks, interface.clj, core.clj, test namespace discovery, brick-test-namespaces, project-test-namespaces, ProcessBuilder classpath, shadow-cljs.edn, olical cljs-test-runner, lazytest, clojure.test deftest, EDN config parsing, JVM opts, POLY_TEST_JVM_OPTS, setup-fn teardown-fn, workspace.edn test-configs, var filter include exclude focus options, subprocess test isolation, clj-namespace predicate, cljs-namespace predicate, include-src-dir, ORG_CORFIELD_EXTERNAL_TEST_RUNNER, shadow-build, node-test, karma target. DO NOT USE FOR: JavaScript or TypeScript source files, frontend UI components, REST API routes."
argument-hint: "[component, feature, or test runner concern]"
user-invokable: true
disable-model-invocation: false
---

## Overview

`polylith-external-test-runner` is a Polylith test runner that executes Clojure and ClojureScript tests in isolated Java subprocesses — avoiding classloader conflicts, daemon thread leaks, and memory accumulation between test runs.

The workspace contains:
- **`components/external-test-runner`** — core test runner logic (Polylith component: `interface.clj` + `core.clj`)
- **`bases/external-test-runner-cli`** — subprocess entry point (`main.clj`) invoked inside the forked JVM
- **`components/util`** — colorizer and string utilities shared by both layers
- **`projects/runner`** — deployable project wiring the component and base together

## Architecture

```
clojure -M:poly test
    └─ Polylith calls interface/create
         └─ core/create builds ProcessBuilder command
              └─ java -cp <classpath> clojure.main -m org.corfield.external-test-runner-cli.main
                   └─ main/-main loads and runs clojure.test / lazytest namespaces
```

**Polylith Brick Pattern**
- `interface.clj` — single-line delegation only: `(defn create [opts] (core/create opts))`
- `core.clj` — all business logic; implements `test-runner-contract/TestRunner` reify

## Common Patterns

### EDN Configuration (three-way merge)
```clojure
(let [env-opts  (-> (System/getenv "ORG_CORFIELD_EXTERNAL_TEST_RUNNER") (or "{}") edn/read-string)
      ws-opts   (-> workspace :settings :test)
      test-opts (merge (:org.corfield/external-test-runner test-settings)
                       (:org.corfield/external-test-runner ws-opts)
                       env-opts)])
;; Precedence: env var > workspace.edn :test-configs > project test-settings
```

### Test Namespace Discovery
```clojure
;; File extension predicates
(defn- clj-namespace?  [{:keys [file-path]}] (or (str/ends-with? file-path ".clj")  (str/ends-with? file-path ".cljc")))
(defn- cljs-namespace? [{:keys [file-path]}] (or (str/ends-with? file-path ".cljs") (str/ends-with? file-path ".cljc")))

;; Selectors respect :include-src-dir option
(let [selectors (cond-> [:test] (:include-src-dir test-opts) (conj :src))])

;; Always wrap in delay — dereference only when running
(def test-nses* (delay (brick-test-namespaces test-opts clj-namespace? bricks bricks-to-test)))
```

### ProcessBuilder Subprocess
```clojure
(let [pb (doto (ProcessBuilder. ^List java-cmd)
           (.redirectOutput ProcessBuilder$Redirect/INHERIT)
           (.redirectError  ProcessBuilder$Redirect/INHERIT))]
  (when-not (-> pb (.start) (.waitFor) (zero?))
    (throw (ex-info "External test runner failed" {:process-ns process-ns}))))
```

### JVM Opts Resolution
```clojure
;; From POLY_TEST_JVM_OPTS env var or poly.test.jvm.opts JVM property
;; If value starts with ":", treat as alias keyword and resolve recursively
(defn- chase-opts-key [aliases k]
  (let [opts-coll (or (:jvm-opts (get aliases k)) (get aliases k))]
    (when (seq opts-coll)
      (into [] (mapcat #(if (string? %) [%] (chase-opts-key aliases %))) opts-coll))))
```

### Shadow-cljs Dispatch
```clojure
(defmulti shadow-test (fn [_ {:keys [target]} _] target))
(defmethod shadow-test :node-test [project-dir {:keys [output-to autorun]} build] ...)
(defmethod shadow-test :karma     [project-dir {:keys [output-to]} build] ...)
;; :cljs-test-runner options: nil (auto-detect), :shadow/:shadow-cljs, :olical, :none/:ignore
```

### Focus / Var Filtering (CLI side)
```clojure
;; In main.clj — filter test vars by :var, :include (metadata kw), :exclude (metadata kw)
(defn var-filter [{:keys [var include exclude]}]
  (let [test-specific  (if var     #((set (keep resolve var)) %) (constantly true))
        test-inclusion (if include #((apply some-fn include) (meta %)) (constantly true))
        test-exclusion (if exclude #((complement (apply some-fn exclude)) (meta %)) (constantly true))]
    #(and (test-specific %) (test-inclusion %) (test-exclusion %))))
```

## Configuration Reference

| Key | Location | Description |
|-----|----------|-------------|
| `:include-src-dir` | test-opts | Also scan `:src` namespaces for tests (default `false`) |
| `:focus {:var [...]}` | test-opts | Run only specific fully-qualified var names |
| `:focus {:include [:kw]}` | test-opts | Run only tests with matching metadata keywords |
| `:focus {:exclude [:kw]}` | test-opts | Skip tests with matching metadata keywords |
| `:cljs-test-runner` | test-opts | `:shadow`/`:shadow-cljs`, `:olical`, `:none`/`:ignore`, or `nil` (auto) |
| `:shadow-build` | test-opts | Shadow-cljs build key to use (default `:test`) |
| `POLY_TEST_JVM_OPTS` | env | Space-separated JVM opts or `:alias-kw` for subprocess |
| `ORG_CORFIELD_EXTERNAL_TEST_RUNNER` | env | Full EDN map to override all config (highest precedence) |

## Project Structure

```
bases/
  external-test-runner-cli/
    src/org/corfield/external_test_runner_cli/main.clj   ← -main entry for subprocess
components/
  external-test-runner/
    src/org/corfield/external_test_runner/
      interface.clj   ← (defn create [opts] (core/create opts))
      core.clj        ← all logic: namespace discovery, ProcessBuilder, Shadow-cljs dispatch
    test/org/corfield/external_test_runner/
      core_test.clj       ← clj-only tests
      interface_test.cljc ← common tests with reader conditionals
      ignored_test.cljs   ← cljs-only (ignored by default config)
  util/
    src/org/corfield/util/interface/
      color.clj       ← colorizer used by both layers
workspace.edn         ← :test-configs with :org.corfield/external-test-runner keys
```

## Pitfalls

- **Don't put logic in `interface.clj`** — it must be a pure thin delegation or Polylith tooling will flag it.
- **Don't forget `^List` type hint** on `java-cmd` vector passed to `ProcessBuilder.` or you'll get a reflection warning.
- **`shadow-cljs.edn` detection is per-project-dir** — use `(.exists (io/file dir "shadow-cljs.edn"))` not classpath scanning.
- **LazyTest is optional** — always use `requiring-resolve` wrapped in try/catch; never hard-depend on `lazytest` at compile time.
- **Delays prevent eager evaluation** — always wrap `brick-test-namespaces` / `project-test-namespaces` calls in `(delay ...)` and check `(seq @test-nses*)` before running.
- **`:none` and `:ignore` are equivalent** for `:cljs-test-runner` — both skip ClojureScript test execution silently.
