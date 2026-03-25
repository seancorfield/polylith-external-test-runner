# Changes

* v0.8.1 -- in progress
  * Address [#20](https://github.com/seancorfield/polylith-external-test-runner/issues/20) by adding `:shadow-optimize` option to control whether Shadow-cljs uses `compile` (unoptimized dev build, the default, equivalent to `:dev`) or `release` (Closure-optimized production build, via `:release`).

* v0.8.0 2ad770c -- 2026-03-24
  * Add `:cljs-test-runner` option to the `:org.corfield/external-test-runner` test settings, which can be set to `:shadow` (or `:shadow-cljs`), `:olical`, or `:none` (or `:ignore`) to control whether and how ClojureScript tests are run.
  * Address [#18](https://github.com/seancorfield/polylith-external-test-runner/issues/18) by adding basic support for running ClojureScript tests via Shadow-cljs, inspired by PR [#19](https://github.com/seancorfield/polylith-external-test-runner/pull/19) from [@itai-spiritt](https://github.com/itai-spiritt).
    * Shadow-cljs should be a `:test` dependency in the project's `deps.edn` file, in addition to a `shadow-cljs.edn` file being present in the project directory, for this to work.
    * Supports `:shadow-build` in test settings to select the Shadow-clj build.
    * Supports `shadow-cljs.edn` with `:build-defaults` and `:target-defaults`.
  * Address [#17](https://github.com/seancorfield/polylith-external-test-runner/issues/17) by adding basic support for running ClojureScript tests via [cljs-test-runner](https://github.com/olical/cljs-test-runner).
  * Update dev/test deps; testing against Polylith 0.3.32.

* v0.7.0 9d885c0 -- 2025-10-08
  * Targets Polylith 0.3.0 and later, with support for dialects.
  * Switch from `bricks-to-test` to `bricks-to-test-all-sources` (a change in Polylith 0.2.22).
  * Drop support for WS v2.0 (`changes` data moved to `project` in v3.0).

* v0.6.1 d0f51c2 -- 2024-12-02
  * Fix [#11](https://github.com/seancorfield/polylith-external-test-runner/issues/11) by supporting `:jvm-opts` in `poly test` property handling.
  * Address [#8](https://github.com/seancorfield/polylith-external-test-runner/issues/8) by noting that the standalone `poly` tool cannot be used with external test runners.

* v0.6.0 90e8ac1 -- 2024-11-19
  * Add support for [lazytest](https://github.com/noahtheduke/lazytest).
  * Polylith 0.2.21 is released and stable: update various references accordingly.

* v0.5.0 d93a147 -- 2024-06-20
  * Address [#5](https://github.com/seancorfield/polylith-external-test-runner/issues/5) and [#6](https://github.com/seancorfield/polylith-external-test-runner/issues/6) by adding support for a `:test` settings key of `:org.corfield/external-test-runner` that can specify configuration for this test runner. Currently, it supports `:include-src-dir` (`true`/`false`) and `:focus` which takes a hash map of `:var`, `:include`, `:exclude` keys to match [Cognitect's test runner](https://github.com/cognitect-labs/test-runner). Documentation TBD.
  * Polylith 0.2.19 is released and stable: update various references accordingly.
  * Update default Clojure version to 1.11.3; also update tools.deps.

* v0.4.0 eb954fe -- 2024-01-25
  * Make the runner compatible with both Polylith 0.2.19-SNAPSHOT and 0.2.18/17.

* v0.3.0 9f6391a -- 2023-10-28
  * Fix [#4](https://github.com/seancorfield/polylith-external-test-runner/issues/4) by guarding `str/split` with `when java-opts`.

* v0.2.0 f208856 -- 2023-02-23
  * Fix [#3](https://github.com/seancorfield/polylith-external-test-runner/issues/3) by switching to `tools.deps` and updating dependencies.

* v0.1.0 337f117 -- 2022-12-09
  * First stable release.

## Development

2022-11-28
* Fix [#2](https://github.com/seancorfield/polylith-external-test-runner/issues/2) by allowing JVM opts to be specified as an alias, and resolved to a vector of strings; added tests for this.
* Fix [#1](https://github.com/seancorfield/polylith-external-test-runner/issues/1) by improving Java discovery.

2022-11-15
* Use a JVM property if an environment variable isn't set to specify the JVM options for the `java` subprocess.

2022-11-14
* Initial version for testing and discussion around [polyfy/polylith#260](https://github.com/polyfy/polylith/issues/260).
