# Contributing

## Prerequisites

- Java 21 (see `.java-version`)
- [Lefthook](https://github.com/evilmartians/lefthook) for local git hooks

## One-time setup

Install Lefthook and register the git hooks:

```bash
# macOS
brew install lefthook

# Other platforms: https://github.com/evilmartians/lefthook#installation
```

```bash
lefthook install
```

This installs a `pre-push` hook that runs the same quality gate CI enforces: `compileKotlin`,
`lintKotlin` (ktlint), `detekt`, and `:bpmn-to-code-core:jacocoTestCoverageVerification`.

## Common Commands

```bash
./gradlew build                                               # full build (compile + ktlint + detekt + tests)
./gradlew check                                              # ktlint + detekt + tests
./gradlew lintKotlin                                         # ktlint formatting check
./gradlew formatKotlin                                       # ktlint auto-fix
./gradlew detekt                                            # detekt static analysis
./gradlew :bpmn-to-code-core:test                            # run core tests only
./gradlew :bpmn-to-code-core:jacocoTestCoverageVerification  # check coverage manually
```

ktlint owns formatting/imports (config in `.editorconfig`); detekt owns semantic/structural
analysis (config in `config/detekt/detekt.yml`). Both are wired into `check`/`build` and gate CI
plus the pre-push hook — no baseline, no silent suppressions. The only scoped exceptions are the
ktor wildcard-import allowance and no hard line-length limit (`.editorconfig`) and the generated
runtime fixture (excluded in both `.editorconfig` and `bpmn-to-code-runtime/build.gradle.kts`).

## Mutation testing (PIT)

[PIT](https://pitest.org/) measures how well the tests actually detect faults, complementing
the line-coverage gate. It runs on demand locally (it is slower than the unit suite, so it is
not part of the pre-push hook) and as a nightly / manual CI workflow. See
[ADR 019](adr/019-mutation-testing-with-pit.md) for scope and thresholds.

```bash
./gradlew pitest --no-configuration-cache                    # all instrumented modules
./gradlew :bpmn-to-code-core:pitest --no-configuration-cache # one module
```

`--no-configuration-cache` is required: the `pitest` task is not configuration-cache
compatible. HTML reports land in each module's `build/reports/pitest/`.

## Skipping hooks

```bash
git push --no-verify  # bypasses lefthook; CI still enforces
```

## Exploring the codebase (optional)

The core follows a hexagonal architecture across several modules
(`domain` → `application` → `adapter`). To navigate the relationships
instead of grepping, some contributors use
[Graphify](https://github.com/Graphify-Labs/graphify), which builds a
local knowledge graph from the source (Kotlin included, AST-only, no API
key required):

```bash
uv tool install graphifyy   # or: pipx install graphifyy
graphify extract . --code-only   # local, ~5s, writes graphify-out/
graphify god-nodes               # most-connected architectural hubs
graphify affected "SomeType"     # what a change would impact
```

This is purely an optional exploration aid — it is **not** a build or
contribution requirement, and its output (`graphify-out/`) is
git-ignored.
