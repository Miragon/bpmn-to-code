# Contributing

Setup, quality gates, tests and pull request rules. The structure of the code is on [Architecture](./architecture), publishing on [Releasing](./releasing), the reasons behind the design in the [Architecture Decisions](./adr/).

## Setup

You need Java 21 (`.java-version`) and, for the pre-push hook, [Lefthook](https://github.com/evilmartians/lefthook#installation):

```bash
brew install lefthook   # macOS; other platforms: see the Lefthook installation guide
lefthook install
```

Optional: a .NET 8 SDK (or a container that provides `dotnet`) to compile the generated C# locally, and Node 22 for the docs site and the BPMN linter.

## Everyday commands

```bash
./gradlew build                          # everything CI builds: compile, ktlint, detekt, all tests
./gradlew :bpmn-to-code-core:test        # tests of one module
./gradlew lintKotlin                     # ktlint check
./gradlew formatKotlin                   # ktlint auto-fix
./gradlew detektMain detektTest          # detekt with type resolution
./gradlew :bpmn-to-code-core:test :bpmn-to-code-core:jacocoTestCoverageVerification   # coverage rule
```

## Quality gates

| Gate | Command | Rule |
|---|---|---|
| ktlint | `./gradlew lintKotlin` | Formatting and imports, configured in `.editorconfig`. Lines target 120 characters; the hard line-length rule is off. |
| detekt | `./gradlew detektMain detektTest` | Configured in `config/detekt/detekt.yml` with `maxIssues: 0`. Plain `detekt` runs without type resolution and can pass where CI fails. |
| Architecture | `./gradlew :bpmn-to-code-architecture-tests:test` | Konsist tests for the layers of core, the imports allowed to the other modules, naming per layer, and one top-level type per file. See [Architecture](./architecture#layers-of-core). |
| Coverage | `./gradlew :<module>:test :<module>:jacocoTestCoverageVerification` | At least 75 % line coverage **per class**. CI verifies core, web, testing and runtime; each module's build file lists the classes it leaves out. |
| Mutation testing | `./gradlew :<module>:pitest --no-configuration-cache` | [PIT](https://pitest.org/) mutation score of at least 80 for core, 90 for web, 95 for runtime and testing. |
| BPMN lint | `npm --prefix tools run lint:bpmn` | bpmnlint over the shared models, see [`tools/README.md`](https://github.com/Miragon/bpmn-to-code/blob/main/tools/README.md). |

There is no baseline and no suppression list for ktlint or detekt: fix the finding, or add a scoped exception to the config with a comment that says why. The exceptions that exist today: ktlint allows wildcard imports for Ktor, and both tools skip the checked-in generated example inside `bpmn-to-code-runtime`.

### What runs where

| | Pre-push hook | Pull request | Nightly |
|---|:---:|:---:|:---:|
| `compileKotlin` | yes | yes | |
| ktlint, detekt | yes | yes | |
| All tests (incl. architecture tests) | | yes | |
| C# compilation test | | yes (CI installs .NET 8) | |
| Coverage rule | core only, see below | core, web, testing, runtime | |
| PIT | | | core, runtime, web, testing |
| Docs build | | when `docs/` changed | |
| BPMN lint | | when a `.bpmn` file or `tools/` changed | |

The hook only runs when the push contains `.kt` or `.kts` files, and the Gradle jobs in CI only run when a module or the build setup changed. The hook does **not** run the tests: its coverage step checks whatever test results are already in `build/`, so run the tests yourself before pushing. `git push --no-verify` skips the hook; CI still enforces everything.

PIT is too slow for a pull request. It runs every night and on demand (`gh workflow run mutation-testing.yml`) and fails when a module drops below its threshold.

## Tests

**Shared models.** `shared/bpmn/{c7,zeebe,operaton}` holds the MiraVelo processes once per engine. The folder is a test resource root of core, gradle, maven, web and testing, so tests load `/bpmn/zeebe/bike-leasing.bpmn`. The web app bundles the bike-leasing models as its examples and the benchmark uses them as input. They follow the [modeling guide](/guide/modeling) and are linted in CI. Deliberately broken models live in the test resources of the module that needs them, not here.

**In-memory models.** Tests that do not exercise BPMN reading build their model in code, with the builders in core's test sources under `io.miragon.bpmn.domain`: `testProcessModel()` and `testProcessModelApi()` for a small model with defaults, `testBikeLeasingModel()` and `testCancelBikeOrderModel()` as mirrors of the shared models, `jobWorkerTask()` for a single node.

**Golden files.** Builder and JSON tests compare the generated text byte for byte with files under `bpmn-to-code-core/src/test/resources/{api,json}`. After an intended change, rewrite them and review the diff:

```bash
./gradlew :bpmn-to-code-core:test -Dgolden.update=true
```

The flag also updates the generated bike-leasing API checked in under `bpmn-to-code-runtime/src/test`. The runtime cannot depend on core, so its `ProcessPath` tests run against that copy, and `RuntimeExampleDriftTest` in core fails when copy and golden file differ.

**Compiled output.** Core's tests compile the generated Kotlin with the embedded Kotlin compiler and the generated Java with `javac`. `CSharpCompilationTest` runs `dotnet build` (target `net8.0`, warnings as errors) and is skipped when there is no `dotnet` on the `PATH`. A green local build therefore says nothing about C# unless you have a .NET 8 SDK or put a wrapper named `dotnet` on the `PATH` that runs the SDK image:

```sh
#!/bin/sh
dir="$(pwd -P)"
exec podman run --rm -v "$dir":"$dir" -w "$dir" mcr.microsoft.com/dotnet/sdk:8.0 dotnet "$@"
```

```bash
PATH=/path/to/wrapper-dir:$PATH ./gradlew :bpmn-to-code-core:test --tests '*CSharpCompilationTest' --rerun
```

CI installs .NET 8, so the test gates every pull request.

**Other layers.** The Gradle module runs smoke tests with Gradle TestKit, the Maven module executes its mojos directly, the web module tests its routes with the Ktor test host, and `ProcessJsonSchemaTest` validates the emitted JSON against the schema in `docs/public/schema`. Unit test conventions are in the `create-unit-test` skill under `.claude/skills/`.

## Pull requests

- The PR title must be a [Conventional Commit](https://www.conventionalcommits.org/) with one of the types `feat`, `fix`, `chore`, `docs`, `refactor`, `ci`, `build`, `test`, `revert`, for example `fix(runtime): leave the compensation boundary event out of a process path`. If the PR has a single commit, that commit message is checked the same way.
- release-please builds the changelog and the next version number from these titles once they are on `main`, so a `feat` or `fix` title is what users read in the release notes.
- Update golden files together with the generator change that causes them, see [Tests](#tests).
- A feature behaves the same in the Gradle plugin, the Maven plugin, the web app and core.
- Examples in code, tests and docs use the MiraVelo models from `shared/bpmn`.

## Docs site

The site is [VitePress](https://vitepress.dev/) in `docs/`:

```bash
cd docs
npm ci
npm run build                 # what CI runs; fails on dead links
npx vitepress dev             # local preview with hot reload
```

`npm run dev` is the maintainer's shortcut and needs the `portless` CLI and macOS. The site is deployed to GitHub Pages as part of a release, see [Releasing](./releasing#documentation).

## Agent tooling

- `CLAUDE.md` (`AGENTS.md` is a symlink to it) holds the instructions for coding agents; `.claude/rules/` holds the Kotlin style rules.
- `.claude/skills/` holds the skills for working **on** this repository: `create-unit-test` and `bpmn-to-code-validate-docs`. Add one as `.claude/skills/<name>/SKILL.md` with `name` and `description` frontmatter.
- `bpmn-to-code-skills/` is a product: the Claude Code plugin shipped to users of bpmn-to-code, documented under [Agent Skills](/skills/). Changes there are user-facing.
