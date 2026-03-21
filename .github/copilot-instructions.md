# polylith-external-test-runner

External subprocess test runner for [Polylith](https://github.com/polyfy/polylith). Runs `clojure.test` and LazyTest suites in a fresh Java subprocess to avoid classloader, daemon thread, and memory issues. Only Clojure itself is required as a dependency in the subprocess.

## Tech Stack

- **Language**: Clojure (`.clj`, `.cljc`), with CLJS detection via Shadow CLJS (`.cljs`, `.cljc`)
- **Build/Deps**: `tools.deps` (`deps.edn`), Polylith (`workspace.edn`, `polylith/clj-poly`)
- **Testing**: `clojure.test`, LazyTest (`lazytest.repl/run-tests`)
- **Subprocess**: `java.lang.ProcessBuilder` with `INHERIT` I/O redirects
- **Monorepo**: Polylith workspace — components, bases, projects

## Architecture

| Layer | Path | Purpose |
|-------|------|---------|
| Interface | `components/external-test-runner/src/.../interface.clj` | Public API — thin delegation only |
| Core | `components/external-test-runner/src/.../core.clj` | All subprocess, option, and namespace logic |
| CLI entry | `bases/external-test-runner-cli/src/.../main.clj` | Subprocess `-main`, test filtering, LazyTest |
| Util | `components/util/src/` | Colorizer and string utilities for output |
| Project | `projects/runner/` | Deployable artifact (git deps root for consumers) |

## Agents

- **Clojure Polylith Test Runner** (`.github/agents/clojure-polylith.agent.md`) — Builds and maintains the Polylith external test runner using Clojure, enforcing the interface/core separation pattern, subprocess execution logic, and test filtering across .clj, .cljs, and .cljc dialects

## Key Conventions

1. `interface.clj` delegates to `core.clj` with zero logic — one-line `defn` per public function
2. Namespace: `kebab-case` (e.g., `org.corfield.external-test-runner.core`); file path: `snake_case` (e.g., `external_test_runner/core.clj`)
3. Options merge order (last wins): project-level → workspace `:test-configs` → `ORG_CORFIELD_EXTERNAL_TEST_RUNNER` env var
4. `chase-opts-key` recursively expands alias keywords from `deps.edn`; handles both `["-Dfoo"]` vector and `{:jvm-opts [...]}` hash-map alias forms
5. `filter-vars!` / `restore-vars!` always paired with `try`/`finally` per test namespace in `main.clj`

## Related Files

- `.github/instructions/clojure-polylith.instructions.md` — coding standards for all `.clj`, `.cljs`, `.cljc`, `.edn` files
- `.github/skills/clojure-polylith/SKILL.md` — domain knowledge for the runner architecture
- `.github/prompts/polylith-external-test-runner.prompt.md` — slash command to invoke the agent
