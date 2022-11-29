# Changes

There have been no stable releases yet.

## Development

2022-11-28
* Fix [#2](https://github.com/seancorfield/polylith-external-test-runner/issues/2) by allowing JVM opts to be specified as an alias, and resolved to a vector of strings; added tests for this.
* Fix [#1](https://github.com/seancorfield/polylith-external-test-runner/issues/1) by improving Java discovery.

2022-11-15
* Use a JVM property if an environmentvariable isn't set to specify the JVM options for the `java` subprocess.

2022-11-14
* Initial version for testing and discussion around [polyfy/polylith#260](https://github.com/polyfy/polylith/issues/260).
