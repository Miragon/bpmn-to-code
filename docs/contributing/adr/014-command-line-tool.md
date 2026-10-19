# 14. Command-line tool

## Context

Every entry point so far is a JVM build tool or an HTTP server. A team that builds with npm, dotnet or a plain CI script needs Gradle or Maven to get the generated API into its build, or has to call the web API. Each ecosystem could get its own package later, but each of them needs something to start first.

## Decision

**A command-line tool is one more thin entry point.** The module `bpmn-to-code-cli` builds the command `bpmn-to-code`. Its commands mirror the plugin tasks and goals and call the same filesystem `*Plugin` classes of core that Gradle and Maven call:

| Command | Gradle task | Maven goal |
|---|---|---|
| `generate-api` | `generateBpmnModelApi` | `generate-bpmn-api` |
| `validate` (experimental) | `validateBpmnModels` | `validate-bpmn` |

The deprecated process JSON ([record 13](013-process-json-deprecation.md)) gets no command.

**Flags only.** Every setting is a flag with the name of the plugin parameter, for example `--baseDir` or `--outputLanguage`. There is no configuration file. Every flag is required and has no default, as in Gradle; a call states everything it relies on. Only `validate` takes `--failOnWarning` and `--disabledRules` ([record 10](010-validation.md)). Relative paths resolve against the directory the tool is started in.

**Exit codes are part of the interface.** `0` on success, `1` when the models fail validation or cannot be read or generated, `2` when the call itself is wrong: an unknown command, a missing flag, a value that does not exist. Findings and errors go to standard error.

**It ships with its own Java runtime.** Each release carries one zip per platform (`linux-x64`, `linux-arm64`, `darwin-arm64`, `darwin-x64`, `win32-x64`): the jars, a runtime trimmed by `jlink` to `java.base`, `java.xml`, `java.compiler` and `java.logging`, and a start script in `bin/` that uses only that runtime. Every archive is built and started once on a runner of its platform. The zip is what every package manager installs: the Homebrew formula `miragon/tap/bpmn-to-code` in `Miragon/homebrew-tap`, the .NET tool `Miragon.BpmnToCode` on nuget.org and the npm package `@miragon/bpmn-to-code`. They need nothing else: Homebrew calls `bin/bpmn-to-code`; a launcher of another ecosystem starts the bundled `java` with the same arguments as that script, `-cp "lib/*" io.miragon.bpmn.cli.MainKt`, because it cannot rely on a shell to run the script.

**The .NET tool is RID-specific.** One package per platform holds that platform's archive next to a launcher of a few lines in C#, and a top-level package points to them, so `dotnet tool install` fetches only the runtime that fits. NuGet packages do not keep the executable bit, so the launcher sets it on the bundled `java` before the first start. RID-specific tools need the .NET 10 SDK. `Miragon.BpmnToCode.MSBuild` runs the tool of its own version through `dotnet tool exec` before the project compiles, so a .NET build generates like a Gradle or Maven build without installing anything.

**The npm package follows esbuild and Biome.** `@miragon/bpmn-to-code` carries only a launcher of a few lines; one package per platform holds that platform's archive and declares its `os` and `cpu`, and the main package lists all of them as optional dependencies, so npm installs only the one that fits.

**It is experimental.** Commands, flags and exit codes may change in a minor release until the tool is declared stable ([record 11](011-compatibility-policy.md)).

**Core is an ordinary dependency.** As in the web app, the module depends on core directly instead of copying its classes. Arguments are parsed with Clikt's core module, which needs no terminal library, and logging goes through `slf4j-simple` at level `warn`.

## Consequences

- A setting added to the plugins needs a flag of the same name, or the entry points drift apart ([record 1](001-product-surfaces-and-distribution.md)).
- Nobody installs Java to use the tool. An archive is about 30 MB, and a run over a handful of models takes about half a second, most of it the start of the JVM.
- A further platform is a further runner in `publish-cli.yml`, and for macOS and Linux a further line in the Homebrew formula.
- The formula follows the newest stable release; it is written and checked by `publish-homebrew.yml` as in the other Miragon CLIs.
- A .NET team on an SDK older than 10 cannot install the tool and has to take an archive instead.
- The archives are not signed. macOS blocks a zip downloaded in a browser until its quarantine flag is removed; Homebrew, `curl` and other package managers do not set it.

## Rejected alternatives

- **A native binary built with GraalVM.** Measured against the jlink archive it starts in 0.04 s instead of 0.5 s, needs a quarter of the memory and downloads at 13 MB instead of 30 MB. But it runs on a second runtime whose reflection and resource metadata has to be traced: a schema-invalid BPMN file the tracing had not seen crashed the binary while the JVM reported it. It also needs GraalVM and about 40 s and 2.5 GB per platform to build. The tool runs once per build, where half a second does not matter. Since packages only call `bin/bpmn-to-code`, a switch stays possible.
- **Requiring an installed Java.** The smallest archive and one build for all platforms, but a .NET or Node team would have to install a JDK after all, which is what the tool is for.

- **A configuration file.** It is a new public contract that Gradle, Maven and the web app would have to read the same way. It can still be added on top of the flags.
- **Defaults as in Maven.** Maven's defaults suit a project laid out like Maven's. Outside the JVM there is no layout to assume, and a default that does not fit only shows up as files in the wrong place.
- **A core in another language, or generators in the target ecosystems fed with a model from core.** Both mean months of work to win back today's output, and the web app and the build plugins could no longer write those languages.
