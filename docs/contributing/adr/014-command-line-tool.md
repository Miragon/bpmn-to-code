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

**Core is an ordinary dependency.** As in the web app, the module depends on core directly instead of copying its classes. Arguments are parsed with Clikt's core module, which needs no terminal library, and logging goes through `slf4j-simple` at level `warn`.

## Consequences

- A setting added to the plugins needs a flag of the same name, or the entry points drift apart ([record 1](001-product-surfaces-and-distribution.md)).
- Every run starts a JVM, so the tool needs Java 21 until a delivery channel brings a runtime along.
- The tool is not published yet. Until it is, its commands and flags are no public contract ([record 11](011-compatibility-policy.md)).

## Rejected alternatives

- **A configuration file.** It is a new public contract that Gradle, Maven and the web app would have to read the same way. It can still be added on top of the flags.
- **Defaults as in Maven.** Maven's defaults suit a project laid out like Maven's. Outside the JVM there is no layout to assume, and a default that does not fit only shows up as files in the wrong place.
- **A core in another language, or generators in the target ecosystems fed with a model from core.** Both mean months of work to win back today's output, and the web app and the build plugins could no longer write those languages.
