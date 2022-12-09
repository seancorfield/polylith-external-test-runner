# polylith-external-test-runner

An external (subprocess) test runner for [Polylith](https://github.com/polyfy/polylith).

Avoids classloader, daemon thread, and memory usage issues
by running tests in a subprocess with only Clojure itself as a
dependency.

> Note: requires Polylith v0.2.17-alpha or later:

```clojure
io.github.polyfy/polylith
{:git/tag "v0.2.17-alpha" :git/sha "a1581cc"
 :deps/root "projects/poly"}
```

## Usage

Ensure you are using a recent version Polylith that supports
external test runners, as shown above.

Add the following dependency to your `:poly` alias to
make this test-runner available:

```clojure
io.github.seancorfield/polylith-external-test-runner
{:git/tag "v0.1.0" :git/sha "337f117"
 :deps/root "projects/runner"}
```

In your `workspace.edn` file, either add this global configuration
to run all of your projects' tests in subprocesses:

```clojure
 :test
 {:create-test-runner
  [org.corfield.external-test-runner.interface/create]}
```

Alternatively, to run just specific projects in subprocesses,
add that `:create-test-runner` entry to those specific projects.

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

The former can be set in your shell process, for the `poly test` command.
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

## License & Copyright

External test runner copyright (c) 2022, Sean Corfield,
Apache Source License 2.0.

Colorizer and string util code copyright (c) 2020-2021, Joakim Tengstrand and others, Eclipse Public Licene 1.0.
