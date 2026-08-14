# ADR 019: Mutation Testing with PIT

## Status
Accepted

## Context

The project already gates on JaCoCo line coverage (≥ 75% per class, see the `subprojects`
block in the root `build.gradle.kts`). Line coverage only proves a line *executed* during a
test — it says nothing about whether any assertion would *notice* if that line's behaviour
changed. A test that calls a method but asserts nothing still counts as full coverage.

We wanted a metric that measures the fault-detection strength of the suite itself:
[PIT (pitest)](https://pitest.org/) mutation testing. PIT introduces small changes
("mutants") into the compiled bytecode — negate a conditional, swap a return value, remove a
void call — and re-runs the tests. A mutant that no test catches ("survived") marks a real
gap; the *mutation score* is the share of mutants killed.

Two facts about this repo shaped the approach:

- **JUnit 6.** The build runs JUnit Jupiter / Platform **6.1.0**, a very new line. PIT drives
  tests through the JUnit Platform launcher via `pitest-junit5-plugin`, whose documentation
  only lists JUnit 5 / Platform 1.x. Compatibility was therefore proven empirically before
  committing to the integration, on `bpmn-to-code-core`, and confirmed working.
- **Kotlin bytecode.** Kotlin emits synthetic members (`$DefaultImpls`, `$Companion`,
  `$WhenMappings`, `kotlinx.serialization`'s `$$serializer` classes, and `$$inlined$…`
  comparators from `sortedBy {}`) plus compiler-generated null/resource intrinsics
  (`Intrinsics.checkNotNull…`, `CloseableKt.closeFinally` from `.use {}`). PIT mutates all of
  these, but no hand-written test can kill them — they inflated the survivor count enough to
  understate the real suite by 6–15 points per module. The commercial Arcmutate Kotlin plugin
  solves this at the engine level; we do not use it and instead exclude the synthetic classes
  and suppress the intrinsic calls via `avoidCallsTo`.

## Decision

Adopt the `info.solidsoft.pitest` Gradle plugin (`gradle-pitest-plugin`) with a **hard
per-module mutation-score gate**, wired to follow the existing JaCoCo convention exactly.

### Scope

Applied to the four logic-bearing modules only — the same set that carries JaCoCo:
`bpmn-to-code-core`, `bpmn-to-code-runtime`, `bpmn-to-code-web`, `bpmn-to-code-testing`.
`bpmn-to-code-gradle` (TestKit), `bpmn-to-code-maven` (thin Java Mojos) and
`bpmn-to-code-architecture-tests` (Konsist, no main sources) are excluded.

### Implementation

- Versions live in `gradle/libs.versions.toml` (`pitest`, `pitestCore`, `pitestJunit5`).
- The plugin is declared `apply false` at the root and applied per-module in each target's own
  `plugins {}` block — mirroring how `jacoco` is wired. Shared configuration sits in a
  `plugins.withId("info.solidsoft.pitest")` block inside `subprojects {}`, next to the
  existing `plugins.withId("jacoco")` block: PIT/engine versions, `targetClasses = io.miragon.*`,
  HTML+XML reports, and the common `excludedClasses`.
- **Excluded classes** mirror the JaCoCo `coverageExclusions` plus the Kotlin/serialization
  synthetics (`*$DefaultImpls`, `*$Companion`, `*$WhenMappings`, `*$$serializer`,
  `*$$inlined$*`). Each module adds its own extras via `excludedClasses.addAll(...)` so they
  extend, not replace, the shared list. `avoidCallsTo` additionally suppresses mutations of the
  Kotlin null/resource intrinsics (while keeping PIT's logging-framework defaults).
- **Thresholds** are per-module `mutationThreshold` values, set a few points below each module's
  coverage so the gate ratchets against regression rather than chasing a target:

  | Module | Mutation coverage | Threshold |
  |---|---|---|
  | `bpmn-to-code-core` | 84% | 80 |
  | `bpmn-to-code-runtime` | 100% | 95 |
  | `bpmn-to-code-testing` | 98% | 95 |
  | `bpmn-to-code-web` | 28% | 22 |

  `bpmn-to-code-web` is the outstanding gap: mutation testing surfaced that its service layer
  (`LibrarySourceProvider`, `WebGenerationService`, `WebJsonGenerationService`) is weakly
  covered by mutation standards even though it clears the 75% JaCoCo line gate. The threshold
  records that reality as a floor; it should be raised as those tests improve. The `core`
  figure is likewise still climbing — its remaining survivors are concentrated in the BPMN
  parsing/dialect adapters (`BpmnStructureReader`, `CamundaDialect`, `ForeignXmlReader`).

- **Configuration cache.** The build enables `org.gradle.configuration-cache` globally. The
  `pitest` task is not configuration-cache compatible, so it must be run with
  `--no-configuration-cache` (the same flag the publish workflows already use). Applying the
  plugin does **not** break the normal cached build — only the `pitest` task needs the flag.

### Running it

Local runs are manual and on demand — PIT is far slower than the unit suite, so it is **not**
added to the Lefthook pre-push hook:

```bash
./gradlew pitest --no-configuration-cache                    # all four modules
./gradlew :bpmn-to-code-core:pitest --no-configuration-cache # one module
```

In CI it runs as a separate, opt-in workflow (`.github/workflows/mutation-testing.yml`) on a
nightly schedule and via manual `workflow_dispatch`, not on the PR path. The job uploads the
HTML reports as an artifact and fails when any module regresses below its threshold.

## Consequences

### Positive
- Adds a fault-detection metric that line coverage cannot provide, and turns it into an
  enforceable regression floor.
- Already surfaced a concrete gap (the web service layer) that the 75% line gate hid.
- Follows the established JaCoCo wiring pattern, so the build stays consistent and the
  per-module exclusion lists have a single obvious home.

### Negative
- PIT is slow, which is why it is nightly/opt-in rather than a PR gate — a regression can land
  on `main` and only be caught by the next scheduled run.
- Kotlin synthetic mutants force an exclusion list that must be kept in sync with the JaCoCo
  exclusions by hand (Arcmutate would remove this need, at a licence cost).
- The `web` threshold is low enough to be a weak gate until its service tests improve.
- Running PIT requires remembering `--no-configuration-cache`; forgetting it fails fast with a
  clear cache error rather than silently.

## Alternatives Considered

**Report-only (no gate).** Simplest and never blocks, but a score nobody enforces tends to
drift; the project already prefers hard gates (JaCoCo, Detekt), so a gate is consistent.

**Run on every PR.** Best feedback latency, but PIT's runtime would dominate the PR build for a
metric that changes slowly — a poor trade for a fast-moving PR queue. Nightly + on-demand keeps
signal without taxing every PR.

**Adopt Arcmutate for first-class Kotlin support.** Would eliminate the synthetic-mutant noise
and the exclusion list, but it is a commercial plugin; the free exclusion approach is adequate
for the current surface.
