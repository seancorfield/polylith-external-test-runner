---
name: "clojure-polylith"
description: "Clojure Polylith monorepo with interface/core brick structure, subprocess test execution via ProcessBuilder, and clojure.test + LazyTest support. USE FOR: polylith bricks, interface.clj, core.clj, test namespace filtering, focus options, var-filter, filter-vars, ProcessBuilder, JVM opts, alias chasing, workspace.edn, deps.edn, clojure.test deftest, lazytest, cljc dialects, shadow-cljs, test configuration, inline src tests, colorizer integration, external test runner, ORG_CORFIELD_EXTERNAL_TEST_RUNNER, chase-opts-key, restore-vars. DO NOT USE FOR: React components, frontend UI, Python code, database migrations, REST API routes."
---

## Overview

This project is a **Polylith external subprocess test runner** for Clojure. It avoids classloader, daemon thread, and memory issues by running `clojure.test` and LazyTest suites in a fresh Java subprocess with only Clojure as a dependency.

The monorepo is organized as a Polylith workspace with:
- **Component**: `components/external-test-runner` — the test runner logic (interface + core)
- **Component**: `components/util` — shared colorizer and string utilities
- **Base**: `bases/external-test-runner-cli` — the subprocess entry point (`-main`)
- **Project**: `projects/runner` — the deployable artifact (deps root for `:poly` alias users)

## Architecture

```
workspace.edn           ← Polylith config, :test-configs, :projects
deps.edn                ← :dev, :poly, :test aliases; alias chains for chase-opts-key
components/
  external-test-runner/
    src/org/corfield/external_test_runner/
      interface.clj     ← Public API: thin delegation to core only
      core.clj          ← All logic: ProcessBuilder, JVM opts, namespace filtering
    test/...            ← *_test.clj / *_test.cljc files
  util/
    src/.../color.clj   ← Colorizer (colorizer-ns source root added to classpath)
bases/
  external-test-runner-cli/
    src/org/corfield/external_test_runner_cli/
      main.clj          ← -main: var-filter, filter-vars!, restore-vars!, lazytest support
projects/
  runner/               ← Git deps root consumers add to :poly alias
```

## Core Patterns

### Interface/Core Delegation

`interface.clj` contains ONLY thin delegating functions — one line per public function:

```clojure
(ns org.corfield.external-test-runner.interface
  (:require [org.corfield.external-test-runner.core :as core]))

(defn create [opts] (core/create opts))
```

### ProcessBuilder Subprocess Execution

```clojure
;; core.clj — java-test-runner
(let [path-sep  (System/getProperty "path.separator")
      classpath (str/join path-sep
                          (->> all-paths
                               (cons (ns->src colorizer-ns))
                               (cons (ns->src process-ns))))
      java-cmd  (-> [(find-java)]
                    (into java-opts)
                    (into options-as-jvm)
                    (into ["-cp" classpath "clojure.main" "-m" process-ns])
                    (into test-args))
      pb        (doto (ProcessBuilder. ^List java-cmd)
                  (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                  (.redirectError  ProcessBuilder$Redirect/INHERIT))]
  (when-not (-> pb (.start) (.waitFor) (zero?))
    (throw (ex-info "External test runner failed" {:process-ns process-ns}))))
```

### JVM Option Resolution via `chase-opts-key`

Recursively resolves alias keywords from `deps.edn`, handling both vector and `{:jvm-opts [...]}` forms:

```clojure
;; :example-opts [:sub-opts "-Dfoo=bar" :nested-opts]
;; :sub-opts ["-Dsub=more"]
;; :nested-opts {:jvm-opts ["-Dnested=even.more"]}
;; (chase-opts-key aliases :example-opts)
;; => ["-Dsub=more" "-Dfoo=bar" "-Dnested=even.more"]

(defn- chase-opts-key [aliases k]
  (let [opts-coll (get aliases k)
        opts-coll (or (:jvm-opts opts-coll) opts-coll)]
    (when (seq opts-coll)
      (into [] (mapcat #(if (string? %) [%] (chase-opts-key aliases %))) opts-coll))))
```

Set via `:poly` alias: `:jvm-opts ["-Dpoly.test.jvm.opts=:example-opts"]`

### Test Option Merging (priority order — later wins)

```clojure
(let [env-opts (edn/read-string (or (System/getenv "ORG_CORFIELD_EXTERNAL_TEST_RUNNER") "{}"))
      ws-opts  (-> workspace :settings :test)
      options  (merge (:org.corfield/external-test-runner test-settings)   ; project-level
                      (:org.corfield/external-test-runner ws-opts)          ; workspace-level
                      env-opts)]                                            ; env var (highest)
  ...)
```

### Test Filtering (`:focus` options)

In `main.clj`, `var-filter` builds a predicate from `:var`, `:include`, `:exclude`:

```clojure
(let [filter-fn (var-filter (:focus options))]
  (try
    (require test-sym)
    (filter-vars! test-sym filter-fn)     ; hide non-matching vars
    (test/run-tests test-sym)
    (finally
      (restore-vars! test-sym))))         ; always restore
```

### LazyTest Integration

```clojure
(let [lazy-run  (try (requiring-resolve 'lazytest.repl/run-tests) (catch Exception _ nil))
      lazy-find (try (requiring-resolve 'lazytest.find/find-var-test-value) (catch Exception _ nil))
      lazy-opts {:var :var-filter :namespace :ns-filter}]
  (cond-> {:error 0 :fail 0 :pass 0 :skip true}
    (contains-tests? test-sym is-test?)
    (merge-summaries (test/run-tests test-sym))
    (and lazy-run lazy-find (contains-tests? test-sym lazy-find))
    (merge-summaries (lazy-run test-sym (set/rename-keys (:focus options) lazy-opts)))))
```

### Inline Src Tests

Public functions in `src/` can include inline test metadata (run when `:include-src-dir true`):

```clojure
(defn bases-msg
  {:test (fn [] (is (= nil (bases-msg [] nil))))}
  [base-names color-mode]
  ...)
```

### Dialect Detection

```clojure
(defn- clj-namespace? [{:keys [file-path]}]
  (or (str/ends-with? file-path ".clj")
      (str/ends-with? file-path ".cljc")))

(defn- cljs-namespace? [{:keys [file-path]}]
  (or (str/ends-with? file-path ".cljs")
      (str/ends-with? file-path ".cljc")))
```

`.cljc` files satisfy both — they appear in both CLJ and CLJS namespace collections.

## workspace.edn Test Configs

```clojure
;; workspace.edn
:test-configs
{:source {:org.corfield/external-test-runner {:include-src-dir true
                                               :focus {:exclude [:integration]}}}
 :dev    {:org.corfield/external-test-runner {:focus {:include [:dev]}}}
 :dummy  {:org.corfield/external-test-runner
          {:focus {:var [org.corfield.external-test-runner.core-test/dummy-test]}}}}
```

Use: `clojure -M:poly test with:source` or `clojure -M:poly test with:source:dev`

## Pitfalls

- **Don't add logic to `interface.clj`** — it breaks Polylith's API contract model.
- **`cljc` files match both predicates** — they'll be included in both CLJ and CLJS test collections; this is intentional.
- **`chase-opts-key` is not cycle-safe** — avoid circular alias references in `deps.edn`.
- **Shadow CLJS tests** are detected but not yet executed — the `cljs-test-runner` only prints informational messages.
- **`restore-vars!` must be in `finally`** — failing to restore vars will corrupt subsequent test runs in the same process.
- **`ns->src` uses classloader resource lookup** — the namespace must be on the classpath at resolution time, not just at subprocess launch time.
