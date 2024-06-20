# Changes

v0.5.0 d93a147 -- 2024-06-20
* Address [#5](https://github.com/seancorfield/polylith-external-test-runner/issues/5) and [#6](https://github.com/seancorfield/polylith-external-test-runner/issues/6) by adding support for a `:test` settings key of `:org.corfield/external-test-runner` that can specify configuration for this test runner. Currently, it supports `:include-src-dir` (`true`/`false`) and `:focus` which takes a hash map of `:var`, `:include`, `:exclude` keys to match [Cognitect's test runner](https://github.com/cognitect-labs/test-runner). Documentation TBD.
* Polylith 0.2.19 is released and stable: update various references accordingly.
* Update default Clojure version to 1.11.3; also update tools.deps.

v0.4.0 eb954fe -- 2024-01-25
* Make the runner compatible with both Polylith 0.2.19-SNAPSHOT and 0.2.18/17.

v0.3.0 9f6391a -- 2023-10-28
* Fix [#4](https://github.com/seancorfield/polylith-external-test-runner/issues/4) by guarding `str/split` with `when java-opts`.

v0.2.0 f208856 -- 2023-02-23
* Fix [#3](https://github.com/seancorfield/polylith-external-test-runner/issues/3) by switching to `tools.deps` and updating dependencies.

v0.1.0 337f117 -- 2022-12-09
* First stable release.

## Development

2022-11-28
* Fix [#2](https://github.com/seancorfield/polylith-external-test-runner/issues/2) by allowing JVM opts to be specified as an alias, and resolved to a vector of strings; added tests for this.
* Fix [#1](https://github.com/seancorfield/polylith-external-test-runner/issues/1) by improving Java discovery.

2022-11-15
* Use a JVM property if an environment variable isn't set to specify the JVM options for the `java` subprocess.

2022-11-14
* Initial version for testing and discussion around [polyfy/polylith#260](https://github.com/polyfy/polylith/issues/260).
