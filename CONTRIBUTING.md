# Contributing

Bug reports and ideas are welcome as [GitHub issues](https://github.com/Miragon/bpmn-to-code/issues). We do not accept external pull requests.

The contributor guide lives on the docs site:

- [Contributing guide](https://miragon.github.io/bpmn-to-code/contributing/): setup, everyday commands, quality gates, tests, pull request rules, docs site
- [Architecture](https://miragon.github.io/bpmn-to-code/contributing/architecture): modules and the layers of core
- [Languages and ecosystems](https://miragon.github.io/bpmn-to-code/contributing/languages): how one Kotlin core reaches the JVM, .NET and Node
- [Releasing](https://miragon.github.io/bpmn-to-code/contributing/releasing): what release-please publishes where
- [Benchmarking the generator](https://miragon.github.io/bpmn-to-code/contributing/benchmark)
- [Architecture decisions](https://miragon.github.io/bpmn-to-code/contributing/adr/)

The sources of these pages are in [`docs/contributing/`](docs/contributing/). Instructions for coding agents are in [`CLAUDE.md`](CLAUDE.md).

## Quick start

You need Java 21 and [Lefthook](https://github.com/evilmartians/lefthook#installation) for the pre-push hook:

```bash
lefthook install
./gradlew build
```

## Building the command-line tool

```bash
./gradlew :bpmn-to-code-cli:cliArchive
./gradlew :bpmn-to-code-cli:installDist
```

`cliArchive` packs the tool with a runtime linked by `jlink` into `bpmn-to-code-cli/build/cli-archive/bpmn-to-code-<version>-<platform>.zip`, for the platform the build runs on. `installDist` builds it without a runtime into `bpmn-to-code-cli/build/install`; its scripts need Java 21 or newer. The C# and TypeScript tests in `packages/` run on that build.

## BPMN models

The shared models in `shared/bpmn` are linted with bpmnlint, see [`tools/README.md`](tools/README.md).
