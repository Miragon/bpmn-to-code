# Concept: Target Languages and Environments

::: info Decided, not built yet
The open questions of this concept were decided on 2026-10-18, see [the decisions](#the-decisions). The command-line tool is built ([ADR 014](../adr/014-command-line-tool)); every further part gets its [ADR](../adr/) in the change that builds it.
:::

How bpmn-to-code could reach teams outside the JVM. This page is the overview; every topic has its own page with the details.

## Who this is for

Today the generator runs inside a Gradle or Maven build, or behind the web app. That fits JVM teams. It does not fit:

- a .NET team that writes workers in C# and has no JDK, Gradle or Maven
- a Node team that writes workers in TypeScript and works with `package.json` scripts
- a frontend team that builds no workers but needs the process steps, for example to show the name of the step an order is in

All three want the same thing: files that are generated during their own build, in their own language, started with their own tooling. Changing how the generated API looks is not a goal.

## Where we are

![Today: Gradle plugin, Maven plugin, web app and testing library call core in-process; core reads BPMN, validates, builds one flow graph and renders it with KotlinPoet, JavaPoet and a C# text writer](../images/target-environments-today.svg)

| Fact | Consequence |
|---|---|
| Every entry point is a JVM build tool or an HTTP server | A C# user needs a JDK plus Gradle or Maven, or has to call the web API |
| The flow graph is computed once and rendered per language ([ADR 007](../adr/007-output-languages)) | A new language is a new renderer, not a new pipeline |
| C# is already written as plain text, without a poet library | The way to render a language that has no such library exists and works |
| The shared definition files have no common model, each language reads the domain itself | Enough while core writes every language; a model handed to other programs would need it first |
| There is no command-line entry point | Nothing outside a build tool can start the generator |

## Where this goes

![Existing entry points call core directly; new delivery channels such as an npm package or a NuGet tool start a command-line tool that calls the same core, which feeds the language packages](../images/target-environments-target.svg)

Core stays what it is. A command-line tool is put in front of it, and each ecosystem gets a thin package that installs and starts that tool.

## What a team would see

For a TypeScript team this would look as follows.

```json
{
  "scripts": {
    "generate": "bpmn-to-code generate --baseDir bpmn --filePattern *.bpmn --processEngine ZEEBE --outputLanguage TYPESCRIPT --outputFolderPath src/process"
  }
}
```

```ts
import { BikeLeasing } from "./process/bikeLeasing";

// a worker
zeebe.createWorker({ taskType: BikeLeasing.jobTypes.validateApplication /* ... */ });

// a frontend
const step = BikeLeasing.flowNodes.serviceTask_validateApplication;
showLabel(step.name); // "Validate application"
```

## What is settled in principle

These points came out of the discussion that led to this concept. They shape every page that follows.

1. **Generation happens at build time.** The tool reads `.bpmn` files and writes source files, which the project then imports. Calling the generator as a library at runtime, or running it in a browser, is not a goal.
2. **Every target language is a fixed package in this repository.** We build and maintain it. There is no plugin interface for third parties and no public intermediate format they could generate from.
3. **Users do not customise the output.** One language, one shape. No user templates, no template overrides.
4. **Every language delivers at least constants and information.** More is optional, see [tiers](./language-package#tiers).
5. **Every language brings the delivery channel of its ecosystem.** TypeScript ships through npm, C# through NuGet, and so on.

## The decisions

| Question | Decision | Page |
|---|---|---|
| Where the code is written | Core writes every language (A) | [Where the code is written](./generation) |
| How languages without a poet library are written | Writers in Kotlin (C1). Kotlin and Java keep KotlinPoet and JavaPoet | [Writers](./writers) |
| The language of core | Core stays Kotlin, behind a command-line tool (A1) | [The language of core](./core-language) |
| How Java travels along | Each package bundles a trimmed Java runtime (B1) | [Delivery channels](./delivery#carrying-java-along) |
| Configuration | Flags only, with the names the Gradle and Maven plugins use (idea 1), plus settings in the BPMN model as today (idea 5) | [Ideas for configuration](./configuration) |

The web app keeps calling core in-process, and Kotlin and Java keep reaching their users through the Gradle and Maven plugins. C# is delivered first, as a `dotnet tool` on NuGet. TypeScript follows, as a new writer in core and an npm package.

## The pages

| Page | Answers | State |
|---|---|---|
| [The language package](./language-package) | What a target language consists of, where its parts live in the repository, which tiers there are, whether it needs a runtime library | pattern settled, runtime libraries open |
| [Where the code is written](./generation) | Whether core writes the code of every language, or hands out a model and lets each environment write its own | decided: core writes every language |
| [Delivery channels](./delivery) | How an npm or NuGet package can start a tool that is not written in JavaScript or C#, and how Java travels along | decided: a bundled Java runtime |
| [The language of core](./core-language) | Whether core should move to Go, Node or Kotlin Multiplatform | decided: core stays Kotlin |
| [Writers](./writers) | How languages without a poet library are rendered: Kotlin code or template files | decided: Kotlin code |
| [Ideas for configuration](./configuration) | Five ways to configure the tool, from flags to a `bpmn-to-code.json`, each compared with tools that work that way | decided: flags only |
| [Further changes](./repository) | What else changes in core and in the release, and which ADRs this touches | follows from the choices |

One question belongs to none of these pages: does C# leave its experimental state before TypeScript starts?
