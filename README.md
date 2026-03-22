# polylith-external-test-runner

An external (subprocess) test runner for [Polylith](https://github.com/polyfy/polylith).

Avoids classloader, daemon thread, and memory usage issues
by running tests in a (Java) subprocess with only Clojure itself as a
dependency.

As of v0.8.0, can run ClojureScript tests if either a `shadow-cljs.edn` file
is present in the project directory, or if
[cljs-test-runner](https://github.com/olical/cljs-test-runner)
is specified as a `:test` dependency in a project's `deps.edn` file.
See also the `:cljs-test-runner` option in **Test Configuration** below.

## Usage

Ensure you are using a recent version Polylith that supports
external test runners and dialects (v0.3.0 or later).

You must be using a `:poly` alias in your `deps.edn` file to run the Polylith
tool and tests, since you need to be able to modify the classpath to add an
external test runnner. You cannot use the standalone `poly` tool for this.

Add the following dependency to your `:poly` alias to
make this test-runner available:

```clojure
io.github.seancorfield/polylith-external-test-runner
{:git/tag "v0.7.0" :git/sha "9d885c0" -- supports Polylith 0.3.0 and later
 :deps/root "projects/runner"}
```

Use `:git/sha "23a0405d0e1080ac53d0b5183479768dc678085a"` (and no `:git/tag`)
if you want to try out the ClojureScript support coming in v0.8.0.

In your `workspace.edn` file, either add this global configuration
to run all of your projects' tests in subprocesses:

```clojure
 :test
 {:create-test-runner
  [org.corfield.external-test-runner.interface/create]}
```

Alternatively, to run just specific projects in subprocesses,
add that `:create-test-runner` entry to those specific projects.

See also **Test Configuration** below for new functionality available with Polylith 0.2.20+.

### Finding Java

The test runner checks the `JAVA_CMD` environment variable and will use
that value if set, else it checks the `JAVA_HOME` environment variable
and will use the value `${JAVA_HOME}/bin/java` is that is set, else it
assumes `java` is on your classpath and can be used as-is.

### Passing JVM Options

Since the tests are executed in a `java` subprocess, you may need to
provide JVM options to control how it runs. You can specify the JVM
options for the subprocess in two ways:
* via the `POLY_TEST_JVM_OPTS` environment variable,
* via the `poly.test.jvm.opts` JVM property.

The former can be set in your shell process, for the `clojure -M:poly test` command.
The latter can be set in the `:poly` alias via:
```clojure
  :jvm-opts ["-Dpoly.test.jvm.opts=..."]
```

The value of the environment variables or the JVM property should either be:
* a space-separated list of all the JVM options you need,
* a Clojure keyword that will be looked up as an alias in your workspace-level `deps.edn` file.

The latter allows multiple options to be specified more easily, and also
allows for other aliases to be used in those vectors of options, which are
looked up recursively (a similar ability has been [proposed for `tools.deps.alpha`](https://clojure.atlassian.net/browse/TDEPS-184)).
See this project's [`deps.edn` file](https://github.com/seancorfield/polylith-external-test-runner/blob/main/deps.edn)
for an example (which is used in the tests for this project).

As of v0.6.1, these aliases can refer to hash maps containing `:jvm-opts` keys,
making it easier to reuse existing aliases for the testing context.

## Test Configuration

> Note: this functionality was added in v0.5.0, for use with Polylith 0.2.20 or later.

By default, this test runner only looks for tests in the `test` directories
of bricks and projects. You can configure it to also looks for tests in the
`src` directories as well, using the `:include-src-dir true` option (the
default is `false`).

In addition, like the [Cognitect's test runner](https://github.com/cognitect-labs/test-runner),
you can specify that only certain tests should be run, either by specifying
a collection of fully-qualified test names (`:var`), or by specifying
keywords to include or exclude tests via metadata (`:include` and `:exclude`).
These can be provided in a `:focus` option as a hash map, and may be combined.
These `:focus` options are also supported for ClojureScript tests when using
the `cljs-test-runner` approach (but not with Shadow-cljs).

As of v0.6.0, this test runner also supports [LazyTest](https://github.com/NoahTheDuke/lazytest).
The same `:focus` options are supported for LazyTest as for Cognitect's test runner
(and under the hood `:var` is mapped to `:var-filter` etc). In addition, you can
specify `:output`, under `:focus`, as a symbol to control the reporting of
test passes and failures. Test suites can contain a mix of both
`clojure.test`-compatible tests and LazyTest tests.

As of v0.8.0, you can control if or how ClojureScript tests are run, via the
`:cljs-test-runner` option:
* not specified -- the test runner will look for `shadow-cljs.edn` in the project or the `olical/cljs-test-runner` dependency, and run ClojureScript tests if either is found (via Shadow-cljs or `olical/cljs-test-runner` respectively); if neither are found and there are files to test, a warning is printed,
* `:shadow` -- will run ClojureScript tests via Shadow-cljs if a `shadow-cljs.edn` file is found in the project directory (it is an error if the file is not found); `:shadow-cljs` is an alias for this value,
* `:olical` -- will run ClojureScript tests via `olical/cljs-test-runner` if the dependency is found (it is an error if the dependency is not found),
* `:none` or `:ignore` -- will not run ClojureScript tests.

As of v0.8.0, if you are using Shadow-cljs, you can specify the
Shadow-cljs build to use for running tests, via the `:shadow-build` option.
The default is the `:test` build, but you can specify any build that
has a supported `:target` (currently `:node-test` and `:karma` are supported).

### Polylith 0.2.20+

You can
provide these options in `workspace.edn` under the `:test-configs` key, and
this test runner looks for the `:org.corfield/external-test-runner` key within
those configurations.

```clojure
;; in your deps.edn file:
polylith/clj-poly {:mvn/version "0.3.32"}

;; in your workspace.edn file:
:test {:create-test-runner [org.corfield.external-test-runner.interface/create]}

:test-configs {:source {:org.corfield/external-test-runner
                        {:include-src-dir true}}
               :slow   {:org.corfield/external-test-runner
                        {:focus {:include [:slow]}}}}
```

Now you can use `clojure -M:poly test with:source` to run tests in both `test` and `src`
directories, or `clojure -M:poly test with:slow` to run only tests defined with `^:slow` or
`^{:slow true}` metadata. You can combine these options as well: `clojure -M:poly test with:source:slow`.

See [Test configuration](https://cljdoc.org/d/polylith/clj-poly/CURRENT/doc/test-runners#test-configuration)
in the Polylith documentation for more details.

> Note: whether you use a single value or a vector for `:create-test-runner` matters in Polylith 0.2.20+ when you use the new `with` syntax for test configurations.

### Configuration Via Environment Variable

If you are using an earlier version of Polylith, you can provide these options
via the `ORG_CORFIELD_EXTERNAL_TEST_RUNNER` environment variable, as a string
that is read as an EDN hash map:

```bash
ORG_CORFIELD_EXTERNAL_TEST_RUNNER="{:include-src-dir true}" clojure -M:poly test
```

> Note: you can also use the environment variable with Polylith 0.2.20+ to override `:include-src-dir` or `:focus` from the `:test-configs` setting, via a simple merge.

## License & Copyright

External test runner copyright (c) 2022-2026, Sean Corfield,
Apache Source License 2.0.

Colorizer and string util code copyright (c) 2020-2021, Joakim Tengstrand and others, Eclipse Public License 1.0.
