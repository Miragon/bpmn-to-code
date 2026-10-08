# Agent Instructions

bpmn-to-code generates type-safe APIs from BPMN process models and validates those models. It ships as a Gradle plugin, a Maven plugin, a web app, a testing library and a runtime library.

## Modules

- `bpmn-to-code-core`: reads BPMN, validates, generates code and process JSON (hexagonal, not published on its own)
- `bpmn-to-code-gradle`: Gradle plugin
- `bpmn-to-code-maven`: Maven plugin (mojos in Java)
- `bpmn-to-code-web`: Ktor web app, shipped as Docker image
- `bpmn-to-code-runtime`: types the generated Kotlin and Java code refers to
- `bpmn-to-code-testing`: test library for BPMN validation rules
- `bpmn-to-code-architecture-tests`: Konsist tests for layers, imports and naming
- `bpmn-to-code-benchmark`: hand-run generator benchmark
- `bpmn-to-code-skills`: Claude Code plugin shipped to users (not a Gradle module)
- `shared/bpmn`: MiraVelo BPMN models all modules test against
- `tools`: bpmnlint for those models
- `docs`: VitePress site

Read before changing structure: [`docs/contributing/architecture.md`](docs/contributing/architecture.md) (modules, layers). Quality gates, test layers and PR rules: [`docs/contributing/index.md`](docs/contributing/index.md). Why things are the way they are: `docs/contributing/adr/`. How BPMN models are named and structured: [`docs/guide/modeling.md`](docs/guide/modeling.md). Kotlin style: `.claude/rules/`.

## Verify

```bash
./gradlew :bpmn-to-code-core:test                 # tests of the module you changed
./gradlew :bpmn-to-code-architecture-tests:test   # after moving code between packages or modules
./gradlew formatKotlin lintKotlin                 # ktlint fix, then check
./gradlew detektMain detektTest                   # detekt with type resolution
./gradlew :bpmn-to-code-core:test -Dgolden.update=true   # rewrite golden files after an intended output change
```

- Lines target 120 characters. ktlint wraps longer lines but never joins shorter ones, so collapse by hand whatever fits on one line.
- No baseline and no suppressions: fix the finding, or add a scoped, commented exception to the config.
- The C# compilation test is skipped without a `dotnet` command, so a green local build proves nothing about generated C#. See the architecture page for how to run it.

## How to work

- **TDD.** Update the domain model first if needed, then write or update tests that express the expected behaviour (red), then implement until they pass (green).
- **Verify after each task.** After every discrete step (a plan phase, a refactor step, a bug fix) run the tests of the affected modules. Prefer targeted module runs over a full build.
- **Consider the testing impact.** New behaviour needs new tests, changed behaviour needs updated tests. When generator output changes, update the golden files with the flag above, review the diff, and check the other output languages.
- **Uniform behaviour.** A feature works the same in Gradle, Maven, web and core. Prefer data in the BPMN model over a setting on one entry point.
- **MiraVelo only.** Examples in code, tests, docs, commits and PRs use the models in `shared/bpmn`. No customer names, locations or process names.
- **Test models.** Build models with the test builders in core's test sources or load the shared models; do not hand-build domain objects or add new BPMN fixtures without need. Use the `create-unit-test` skill for test conventions.

## GitHub

- Use the `gh` CLI for GitHub operations.
- PR titles are Conventional Commits (`feat`, `fix`, `chore`, `docs`, `refactor`, `ci`, `build`, `test`, `revert`); CI enforces it and the changelog is built from them.
- Keep commit messages and PR descriptions short. Focus on what changed and why.
- For issues: write a summary, current state, and desired state. Give a high-level overview of technical impact (breaking or not). Focus on behavior, not implementation details.

## Personality

You are a knowledgeable colleague, not someone who passively takes orders. If something proposed doesn't look right, suggest corrections, ask critical questions, and push back where needed. Challenge ideas that could benefit from further improvement or iterative refinement rather than just accepting them at face value.

## Skills

Skills for working on this repository live in `.claude/skills/<skill-name>/SKILL.md`. The skills shipped to users live in `bpmn-to-code-skills/` and are a product surface.
