---
name: "polylith-external-test-runner"
description: "Build and extend the Polylith external test runner, including subprocess execution, test filtering, JVM option resolution, and multi-dialect (clj/cljs/cljc) namespace support"
agent: "Clojure Polylith Test Runner"
argument-hint: "[feature or fix] [requirements or context]"
---

You are working on the **Polylith external subprocess test runner** for Clojure (`io.github.seancorfield/polylith-external-test-runner`).

This runner avoids classloader, daemon thread, and memory issues by executing `clojure.test` and LazyTest suites in a fresh Java subprocess with only Clojure as a dependency.

## Task

@Clojure Polylith Test Runner — please complete the following:

${input:task:Describe what you want to build or fix — e.g., "add support for :timeout focus option", "fix chase-opts-key handling for map aliases", "add CLJS test execution via Shadow CLJS"}

## Project Context

**Architecture:**
- `components/external-test-runner/src/org/corfield/external_test_runner/interface.clj` — public API (thin delegation only)
- `components/external-test-runner/src/org/corfield/external_test_runner/core.clj` — all subprocess and option logic
- `bases/external-test-runner-cli/src/org/corfield/external_test_runner_cli/main.clj` — subprocess `-main`, `var-filter`, `filter-vars!`, `restore-vars!`

**Key conventions to preserve:**
- `interface.clj` delegates to `core.clj` with zero logic of its own
- `ProcessBuilder` with `INHERIT` redirects for both stdout and stderr
- `chase-opts-key` recursively expands alias keywords from `deps.edn`, handling both vector and `{:jvm-opts [...]}` forms
- Options merge order: project-level → workspace-level → `ORG_CORFIELD_EXTERNAL_TEST_RUNNER` env var
- `filter-vars!` / `restore-vars!` always paired with `try`/`finally` per namespace
- LazyTest detected at runtime via `requiring-resolve`; `:focus` keys remapped via `lazy-opts`
- `.cljc` files satisfy both `clj-namespace?` and `cljs-namespace?` predicates

**Focus areas:**
- Subprocess classpath construction (all-paths + ns->src for colorizer and process-ns)
- JVM option resolution via `chase-opts-key` and alias chasing
- Test namespace filtering with `:var`, `:include`, `:exclude` under `:focus`
- Dialect support: `.clj`, `.cljs`, `.cljc` namespace detection
- `workspace.edn` `:test-configs` and `ORG_CORFIELD_EXTERNAL_TEST_RUNNER` env var merging

**Validation:**
- Run `./run-tests.sh` or `clojure -M:poly test` after changes
- Check that test filtering works with `clojure -M:poly test with:source` and `with:dummy` configs from `workspace.edn`
