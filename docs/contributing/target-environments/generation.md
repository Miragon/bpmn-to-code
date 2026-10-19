# Where the Code Is Written

Decided: option A, core writes the code of every language. This page sets that against the alternative it was chosen over: core hands out a model, and each environment writes its own language.

![Option A: core reads BPMN and writes Kotlin, Java, C# and TypeScript itself. Option B: core reads BPMN and hands out an API model as JSON; core writes Kotlin and Java from it, an npm package writes TypeScript with a generator written in TypeScript, a NuGet package writes C# with a generator written in C#](../images/target-environments-where-code-is-written.svg)

## The two options

**A. Core writes every language.** All writers are Kotlin code in core. The package of an ecosystem only starts the command-line tool, see [delivery channels](./delivery).

**B. Each environment writes its own language.** Core reads the BPMN files, validates them and stops at a model, which it hands out as JSON. The generator for TypeScript is written in TypeScript and lives in the npm package; the one for C# is written in C# and lives in the NuGet package. Kotlin and Java stay in core.

## Side by side

| | A. Core writes every language | B. Each environment writes its own |
|---|---|---|
| Where a writer lives | `builder/<language>/` in core | In the package of its ecosystem |
| Written in | Kotlin, for every language | The language it generates |
| Libraries for the output language | None beyond Kotlin and Java: text is assembled by hand, see [writers](./writers) | The ecosystem's own, for example the TypeScript compiler API for TypeScript or Roslyn for C# |
| What a run starts | One program | Two: core for the model, then the generator of the environment |
| Java on the machine | Needed, see [carrying Java along](./delivery#carrying-java-along) | Still needed, because core produces the model |
| Same result in every entry point | Yes: Gradle, Maven, the web app and the command line can all write every language | No: the web app and a Gradle build can only write TypeScript if Node is there too |
| The model between core and writers | Internal, free to change with every release | A format that two programs have to agree on, with a version |
| Names of generated identifiers | Derived in one place | Either core puts the finished names for each language into the model, or every generator derives them again and has to arrive at the same result |
| Collision and reserved-name checks | In core, before anything is written | Stay in core, so core still has to know the rules of every language |
| Tests | One test suite, one set of expected files | One test suite per ecosystem, plus tests that core and generator fit together |
| Who can work on a language | Anyone who writes Kotlin | Someone who writes that language; we maintain generators in several languages |
| Release | The package is a thin starter | Each package carries real code that has to be released in step with core |

## What B would need first

- **A complete model.** The flow graph covers the elements and their successors. The shared definitions (service tasks, messages, signals, errors, escalations, variables) have no model of their own today; every writer reads the domain directly. B needs both in one document that can be written as JSON.
- **A decision on names.** Kotlin, Java and C# derive their identifiers in three different ways today, and the checks for collisions and reserved names rely on exactly those derivations.
- **A rule for versions.** Core and generator are released together and only work in the same version, as generator and runtime library do today.

The first point is worth doing in either option, see [further changes](./repository).

## What B does not change

- Java is still needed wherever BPMN files are read. B moves the writing of code out of the JVM, not the reading of models.
- The [settled points](./#what-is-settled-in-principle) stay: the generators are ours, in this repository, and the model is a format between our own packages. Once those packages are released separately, that format is a contract in practice, even if nobody else is meant to read it.

One variant removes Java for some teams: the model is written once, by the build of the service that owns the BPMN files, and passed on as a file. A frontend team then generates its TypeScript from that file with nothing but the npm package. That only works where one team is willing to publish the model for the other.

## What it means for the other pages

| Page | With A | With B |
|---|---|---|
| [The language package](./language-package) | The writer is a folder in core | The writer moves into `packages/<registry>/`, next to the delivery channel |
| [Delivery channels](./delivery) | The package is a starter of a few lines | The package is a program of its own that calls the starter first |
| [Writers](./writers) | One decision for all further languages | A decision per ecosystem, made with the tools of that ecosystem |
| [The language of core](./core-language) | Unaffected | Unaffected; core stays the only place that reads BPMN |

## In between

The choice does not have to be the same for every language. A is the default that costs nothing new. A single language could move to B later, when writing it as text in Kotlin turns out to be the harder way, and the model it needs would then already exist.

Back to the [overview](./).
