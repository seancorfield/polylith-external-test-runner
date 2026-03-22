---
name: "clojure-polylith"
description: "Polylith brick structure (interface.clj + core.clj), org.corfield top-namespace with kebab-case namespaces and underscore file paths, EDN config merging pattern, ProcessBuilder subprocess invocation, and clojure.test conventions used throughout this project"
applyTo: "**/*.{clj,cljs,cljc}"
---

## Polylith Brick Structure

- Every component has exactly two source files: `interface.clj` (public API) and `core.clj` (implementation) — never add business logic to `interface.clj`.
- `interface.clj` must contain only thin delegation: `(defn create [opts] (core/create opts))` — one line per public function, no `let` bindings, no conditionals.
- Bases (e.g., `external-test-runner-cli`) expose a `-main` entry point directly; they do not follow the interface/core split.

```clojure
;; interface.clj — correct
(ns org.corfield.external-test-runner.interface
  (:require [org.corfield.external-test-runner.core :as core]))

(defn create [opts]
  (core/create opts))

;; interface.clj — wrong (business logic in interface)
(defn create [{:keys [workspace] :as opts}]
  (when workspace
    (core/create opts)))
```

## Namespace and File Naming

- Top-level namespace is always `org.corfield` — never use a different organization prefix.
- Namespace segments use kebab-case (e.g., `external-test-runner`, `external-test-runner-cli`).
- File paths use underscores for hyphens (e.g., `external_test_runner/core.clj`, `external_test_runner_cli/main.clj`).
- Test namespaces append `-test` suffix: `org.corfield.external-test-runner.core-test` in `core_test.clj`.

```clojure
;; Correct namespace declaration matching file path
;; File: components/external-test-runner/src/org/corfield/external_test_runner/core.clj
(ns org.corfield.external-test-runner.core ...)

;; File: components/external-test-runner/test/org/corfield/external_test_runner/core_test.clj
(ns org.corfield.external-test-runner.core-test
  (:require [clojure.test :refer [deftest is]] ...))
```

## EDN Configuration Merging

- The three-way merge order is: project `test-settings` → workspace `test-configs` → `ORG_CORFIELD_EXTERNAL_TEST_RUNNER` env var (env var wins).
- Always use `(or "{}" ...)` as the default before `edn/read-string` to avoid nil parse errors.
- Configuration keys live under `:org.corfield/external-test-runner` within each level.

```clojure
(let [env-opts  (-> (System/getenv "ORG_CORFIELD_EXTERNAL_TEST_RUNNER")
                    (or "{}")
                    (edn/read-string))
      ws-opts   (-> workspace :settings :test)
      test-opts (merge (:org.corfield/external-test-runner test-settings)
                       (:org.corfield/external-test-runner ws-opts)
                       env-opts)])
```

## ProcessBuilder Subprocess Invocation

- Always redirect both stdout and stderr to `INHERIT` so output streams to the parent process.
- Throw `ex-info` with a descriptive message and data map on non-zero exit code.
- Use `^List` type hint on the command vector passed to `ProcessBuilder.` constructor.
- Resolve `java` binary via `find-java` (checks `JAVA_CMD`, then `JAVA_HOME/bin/java`, then falls back to `"java"`).

```clojure
(let [pb (doto (ProcessBuilder. ^List java-cmd)
           (.redirectOutput ProcessBuilder$Redirect/INHERIT)
           (.redirectError  ProcessBuilder$Redirect/INHERIT))]
  (when-not (-> pb (.start) (.waitFor) (zero?))
    (throw (ex-info "External test runner failed" {:process-ns process-ns}))))
```

## Test Namespace Discovery

- Use `clj-namespace?` (ends with `.clj` or `.cljc`) and `cljs-namespace?` (ends with `.cljs` or `.cljc`) predicates to filter by extension.
- Wrap discovered namespace collections in `(delay ...)` to defer evaluation; dereference with `@` only when actually running tests.
- Respect `:include-src-dir true` by adding `:src` to the selectors vector alongside `:test`.

```clojure
(defn- clj-namespace? [{:keys [file-path]}]
  (or (str/ends-with? file-path ".clj")
      (str/ends-with? file-path ".cljc")))

;; Use delay to defer discovery
(def test-nses* (delay (brick-test-namespaces test-opts clj-namespace? bricks bricks-to-test)))
```

## clojure.test Conventions

- Import test functions explicitly: `(:require [clojure.test :refer [deftest is]])`.
- Test files go in `test/` subdirectory of the brick, mirroring the `src/` namespace path.
- Use inline `:test` metadata on `defn` in `src/` files for self-contained unit tests on pure functions (avoids a separate test file for trivial assertions).
- Reader conditionals (`#?(:clj ... :cljs ...)`) are required in `.cljc` test files when requiring platform-specific namespaces.

```clojure
;; Inline :test metadata in src/ (core.clj)
(defn bases-msg
  {:test (fn [] (is (= nil (bases-msg [] nil))))}
  [base-names color-mode]
  (when (seq base-names)
    [(color/base (str/join ", " base-names) color-mode)]))

;; Standard deftest in test/ (core_test.clj)
(deftest dummy-test
  (is (= 1 1)))

;; Reader conditional in .cljc test file
(ns org.corfield.external-test-runner.interface-test
  (:require [clojure.test :refer [deftest is]]
            #?(:clj [org.corfield.external-test-runner.interface])))
```

## Shadow-cljs Dispatch

- Use `defmulti` dispatching on `:target` key from the build map — never use `cond` for target dispatch.
- Read `shadow-cljs.edn` using `edn/read-string` + `slurp` wrapped in an `io/file`; only read if file exists.
- Compile via `npx shadow-cljs compile <build>` before running any target.
- Support `:node-test` (run `node output-to`) and `:karma` (run `npx karma start --single-run`); throw for unsupported targets.

```clojure
(defmulti shadow-test (fn [_ {:keys [target]} _] target))

(defmethod shadow-test :node-test
  [project-dir {:keys [output-to autorun]} build]
  (shadow-compile project-dir build)
  (when-not autorun
    (run-cmd project-dir ["node" output-to] "Shadow-cljs node test failed" {:output-to output-to})))
```
