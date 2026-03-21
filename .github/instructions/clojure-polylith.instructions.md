---
name: "clojure-polylith"
description: "Polylith interface/core delegation pattern, kebab-case namespace with snake_case file naming, test filtering via focus options, subprocess classpath construction, and inline src-test metadata conventions found in this project"
applyTo: "**/*.{clj,cljs,cljc,edn}"
---

## Interface/Core Separation

- `interface.clj` must contain **only thin delegation functions** — no logic, no state, no side-effects.
  - ✅ `(defn create [opts] (core/create opts))`
  - ❌ `(defn create [opts] (when (:debug opts) (println "...")) (core/create opts))`
- All implementation logic lives exclusively in `core.clj`.
- The `interface` namespace is the public API contract for other bricks; `core` is private implementation.

```clojure
;; interface.clj — delegate only
(ns org.corfield.external-test-runner.interface
  (:require [org.corfield.external-test-runner.core :as core]))

(defn create [opts]
  (core/create opts))
```

## Namespace and File Naming

- Namespaces use **kebab-case**: `org.corfield.external-test-runner.core`
- File paths use **snake_case** segment names: `org/corfield/external_test_runner/core.clj`
- Always align namespace declaration with the physical file path — a mismatch will cause `require` failures.

```clojure
;; File: src/org/corfield/external_test_runner/core.clj
(ns org.corfield.external-test-runner.core ...)
```

## Subprocess Classpath Construction

- Build classpath from `all-paths` (provided by Polylith) **plus** the source roots of `colorizer-ns` and `process-ns` (resolved via `ns->src`).
- Use `path.separator` system property to join paths portably.
- Launch with `ProcessBuilder`, redirect both stdout and stderr to `INHERIT`.
- Throw `ex-info` with `{:process-ns process-ns}` on non-zero exit code.

```clojure
(let [path-sep  (System/getProperty "path.separator")
      classpath (str/join path-sep
                          (->> all-paths
                               (cons (ns->src colorizer-ns))
                               (cons (ns->src process-ns))))
      pb        (doto (ProcessBuilder. ^List java-cmd)
                  (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                  (.redirectError  ProcessBuilder$Redirect/INHERIT))]
  (when-not (-> pb (.start) (.waitFor) (zero?))
    (throw (ex-info "External test runner failed" {:process-ns process-ns}))))
```

## Test Option Merging

- Merge test configuration options in this priority order (later wins):
  1. Project-level `:org.corfield/external-test-runner` from `test-settings`
  2. Workspace-level `:org.corfield/external-test-runner` from `workspace :settings :test`
  3. `ORG_CORFIELD_EXTERNAL_TEST_RUNNER` environment variable (parsed as EDN)

```clojure
(let [env-opts (-> (System/getenv "ORG_CORFIELD_EXTERNAL_TEST_RUNNER")
                   (or "{}")
                   (edn/read-string))
      ws-opts  (-> workspace :settings :test)
      options  (merge (:org.corfield/external-test-runner test-settings)
                      (:org.corfield/external-test-runner ws-opts)
                      env-opts)]
  ...)
```

## JVM Option Resolution via `chase-opts-key`

- JVM options may be given as a space-separated string, or as an alias keyword (`:example-opts`) resolved from `deps.edn` aliases.
- `chase-opts-key` recursively expands alias keywords; handles both `["-Dfoo=bar"]` vector and `{:jvm-opts [...]}` hash-map forms.
- Use `get-project-aliases` (merging `:root` and `:project` edn maps) as the alias source.

```clojure
(defn- chase-opts-key [aliases k]
  (let [opts-coll (get aliases k)
        opts-coll (or (:jvm-opts opts-coll) opts-coll)]
    (when (seq opts-coll)
      (into [] (mapcat #(if (string? %) [%] (chase-opts-key aliases %))) opts-coll))))
```

## Test Filtering (focus options)

- Support `:var`, `:include`, and `:exclude` under `:focus` key in options.
- Use `var-filter` to build a predicate, `filter-vars!` to hide non-matching vars before running, and `restore-vars!` in `finally` to restore them.
- For LazyTest, rename `:focus` keys using `{:var :var-filter :namespace :ns-filter}` before passing.

```clojure
;; Always restore vars, even on exception
(try
  (require test-sym)
  (filter-vars! test-sym filter-fn)
  (test/run-tests test-sym)
  (finally
    (restore-vars! test-sym)))
```

## Inline Src Tests

- Public functions may declare inline tests using `{:test (fn [] ...)}` metadata — this is the pattern used in `core.clj` on `bases-msg`.
- These are included when `:include-src-dir true` is set in options, causing `(conj :src)` to be added to the namespace selectors.

```clojure
(defn bases-msg
  {:test (fn [] (is (= nil (bases-msg [] nil))))}
  [base-names color-mode]
  (when (seq base-names)
    [(color/base (str/join ", " base-names) color-mode)]))
```

## Polylith Brick Structure

- Components: `components/<name>/src` (implementation), `components/<name>/test` (tests)
- Bases: `bases/<name>/src` (entry points), `bases/<name>/test` (tests)
- Each brick has its own `deps.edn` declaring only its direct dependencies
- Test files follow `*_test.clj` or `*_test.cljc` naming in the `test/` directory tree

## dialect Detection

- `.clj` and `.cljc` files are CLJ namespaces (`clj-namespace?` predicate)
- `.cljs` and `.cljc` files are CLJS namespaces (`cljs-namespace?` predicate)
- Both predicates check `file-path` using `str/ends-with?`
- `cljc` files satisfy both predicates and are included in both CLJ and CLJS namespace collections
