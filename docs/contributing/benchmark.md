# Benchmarking the Generator

`bpmn-to-code-benchmark` measures the generator of your working copy against a published release, so a change that
makes generation slower (or faster) shows up as a number instead of an impression. It is run by hand, not by CI.

The measuring is done by [JMH](https://github.com/openjdk/jmh). Both generators run through the in-memory entry point
the web module uses (`CreateProcessApiInMemoryPlugin`), each in its own forked JVMs. The baseline is the released Gradle
plugin jar, which bundles `bpmn-to-code-core`.

## Running it

```bash
# compare this build with 5.2.0 — prints JMH's result table per generator and a comparison table
./gradlew :bpmn-to-code-benchmark:benchmark

# a single generator
./gradlew :bpmn-to-code-benchmark:benchmarkCurrent
./gradlew :bpmn-to-code-benchmark:benchmarkBaseline
```

| Property | Default | Meaning |
|---|---|---|
| `-Pmode` | `warm` | What to measure, see below |
| `-Pscenario` | all | Only scenarios whose name contains this text, e.g. `bike-leasing` |
| `-PbaselineVersion` | `5.2.0` | The release to compare with |

JMH's raw results are written to `bpmn-to-code-benchmark/build/benchmark/` as JSON.

Run it on an otherwise idle machine. `warm` over all scenarios takes about 16 minutes, `warm` with
`-Pscenario=bike-leasing` about four.

## Modes

| Mode | Measures | Stands for |
|---|---|---|
| `warm` | ms per generation, ms spent reading the BPMN, MB allocated, generated files and lines | a long-running process, e.g. a Gradle daemon |
| `cold` | the first and the second generation in a fresh JVM with one CPU and the web image's heap settings | the first web requests after a (re)start |
| `interpreted` | `warm`, with the JIT switched off (`-Xint`); skips the heavy scenarios | the web app, whose little traffic and ¼ CPU rarely let the JIT compile the generator |
| `profile` | a Java Flight Recording of the first matching scenario, per output language | finding the next hotspot |
| `compile` | javac time and class files for the generated Java | what the generated code costs projects that compile it |

## Scenarios

| Scenario | Input |
|---|---|
| `bike-leasing`, `bike-leasing-c7` | the web example (42 nodes), for Zeebe and Camunda 7 |
| `3-files` | bike-leasing, membership and welcome-package — the most the web accepts |
| `100-files` | 100 renamed copies of those three, for projects with many models |
| `synthetic-250/1000/2000` | generated Zeebe processes of that many nodes, diagram included, for big models |

`cancel-bike-order` is left out on purpose: 5.2.0 rejects its catch-all error boundary event.

## Reading the comparison

Every cell reads `baseline → current (ratio)`, where the ratio is `current / baseline`. Below `1.00×` the current build
is faster or smaller.

Times carry JMH's error margin, e.g. `4.77 ±0.19 → 5.51 ±0.13 (1.16×)`. Only trust a difference whose margins do not
overlap. A margin that is large compared with its value means the run was disturbed — repeat it.

JMH's own result table lists the counters (`files`, `lines`, `class files`, `CPU ms`) summed over all iterations; the
comparison table shows them per generation.

## Finding a hotspot

`-Pmode=profile` writes one `profile.jfr` per output language below `bpmn-to-code-benchmark/build/benchmark/recordings/`.
Open it in IntelliJ or JDK Mission Control for a flame graph, or print the hottest methods:

```bash
jfr view hot-methods <path to profile.jfr>
```
