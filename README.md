# polylith-external-test-runner

An external (subprocess) test runner for [Polylith](https://github.com/polyfy/polylith).

Avoids classloader, daemon thread, and memory usage issues
by running tests in a subprocess with only Clojure itself as a
dependency.

> Note: this currently requires that you use a fork of Polylith that supports external test runners:

```clojure
io.github.seancorfield/polylith
{:git/sha "9cbdc21f4be9307289242237551698191e05b4dd"
 :deps/root "projects/poly"}
```

## usage:

Ensure you are using the fork of Polylith that supports
external test runners, shown above.

Add the following dependency to your `:poly` alias to
make this test-runner available:

```clojure
io.github.seancorfield/polylith-external-test-runner
{:git/sha "..."
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

## License & Copyright

External test runner copyright (c) 2022, Sean Corfield,
Apache Source License 2.0.

Colorizer and string util code copyright (c) 2020-2021, Joakim Tengstrand and others, Eclipse Public Licene 1.0.
