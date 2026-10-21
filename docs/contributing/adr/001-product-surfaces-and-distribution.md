# 1. Product surfaces, distribution and uniform entry points

## Context

bpmn-to-code reaches its users in several ways: as a build plugin, in the browser, as a test library and as a small library that generated code depends on. All of them have to produce the same result for the same BPMN files, and a user should not have to resolve a diamond of internal artifacts to get there.

## Decision

**Published surfaces.** Ten things are shipped:

- the Gradle plugin `io.miragon.bpmn-to-code-gradle` (Gradle Plugin Portal),
- the Maven plugin `io.miragon:bpmn-to-code-maven`,
- the testing library `io.miragon:bpmn-to-code-testing` ([record 10](010-validation.md)),
- the runtime library `io.miragon:bpmn-to-code-runtime` ([record 8](008-runtime-library-and-process-paths.md)),
- the web application as the Docker image `miragon/bpmn-to-code-web` ([record 12](012-web-application.md)),
- the command-line tool as one archive per platform on the GitHub release, with its own Java runtime, and as the Homebrew formula `miragon/tap/bpmn-to-code` ([record 14](014-command-line-tool.md)),
- the same tool as the .NET tool `Miragon.BpmnToCode` on nuget.org ([record 14](014-command-line-tool.md)),
- the C# runtime package `Miragon.BpmnToCode.Runtime` on nuget.org ([record 7](007-output-languages.md)),
- the MSBuild integration `Miragon.BpmnToCode.MSBuild` on nuget.org, which generates during `dotnet build` through the .NET tool ([record 14](014-command-line-tool.md)),
- the agent skills in `bpmn-to-code-skills/`, a Claude Code plugin installed through the marketplace manifest in the repository root.

**Core is not published.** `bpmn-to-code-core` has no coordinates. The Gradle plugin, the Maven plugin and the testing library declare it `compileOnly` and copy its compiled classes into their own jar; core's third-party dependencies are declared again in each of them. Nothing is relocated. Only the web module and the command-line tool use core as an ordinary project dependency.

**What of core is public.** Core's `domain` package is a supported API for exactly one purpose: writing custom validation rules against `bpmn-to-code-testing`. Everything else in core is internal, including the inbound adapters the wrappers call.

**Entry points are thin and uniform.** Gradle, Maven, web and the command-line tool ([record 14](014-command-line-tool.md)) contain no generation logic. They translate their configuration into a call of one of core's inbound `*Plugin` classes ([record 2](002-hexagonal-core.md)), so a feature behaves identically everywhere. An option that shapes the output belongs into the BPMN model, not into a plugin setting: `variantName` is a process extension property ([record 5](005-one-api-per-file-deterministic-generation.md)), and generation accepts no validation settings from any entry point.

An entry point may differ only in what its host requires:

- Gradle takes part in up-to-date checks and the build cache. It asks core which files a pattern matches, so Gradle and the generator can never see different inputs, and it declares the generated directory as an output only when it lies below the build directory.
- Gradle adds the runtime library in its own version as an `implementation` dependency once the `java` plugin is applied. Maven users declare it themselves.
- Neither plugin binds itself to the build lifecycle: Gradle only registers tasks, the Maven goals have no default phase.
- The web application fixes the package name and caps the number of files per request.

**Agent skills carry the library version.** They are not a Gradle module; release-please bumps the version in their `plugin.json` with every release ([record 15](015-agent-skills.md)).

Parameters and defaults are listed in [Configuration](/guide/configuration).

## Consequences

- Users add one plugin or one test dependency and get a working generator; there is no core version to align.
- The classes of `io.miragon.bpmn.domain` exist in three published jars. Changing a domain type is a change to the testing library's API ([record 11](011-compatibility-policy.md)).
- FreeMarker and `camunda-bpmn-model` reach the user's build classpath, `camunda-bpmn-model` and AssertJ the test classpath, all under their original package names.
- Core's dependency list is maintained in every wrapper by hand. A dependency a wrapper forgets fails only when the published plugin runs, which is why the Gradle module resolves its own published plugin in a smoke test.
- A skill fix reaches users only with the next release.
- A setting that exists in one entry point only is a defect, not a feature.

## Rejected alternatives

- **Publish core as its own artifact.** It would make all of core a public contract and add a fourth coordinate users have to keep in step.
- **Shade and relocate core's dependencies.** Custom rules compile against `io.miragon.bpmn.domain`, and relocation would complicate exactly the jar that exposes it. Not needed while the dependency set is this small.
- **Behaviour switches as plugin settings.** `enableVariants` was one. It had to be plumbed through every entry point and never reached all of them: the validation tasks and the testing library had no such setting and behaved differently from generation.
