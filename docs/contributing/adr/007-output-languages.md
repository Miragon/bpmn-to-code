# 7. Output languages: `FlowGraph` and templates

## Context

The generator emits the same API in Kotlin, Java, C# and TypeScript. The languages differ in syntax, in how a singleton or a nested type is written, and in what tooling exists on the JVM to produce their source. What a node is called, which facets it has and which elements follow it does not differ, and must not: an API that means something else per language cannot be documented once.

## Decision

**What is language-independent is computed once.** Before any source is written, a process model is turned into a `FlowGraph`: a flat, language-neutral list of nodes with their generated names, their facets and their successors. It lives in the `codegen/flow` package. The builders only render it.

**One pair of builders per language.** Each `OutputLanguage` has a `ProcessApiBuilder` for a Process API and a `SharedDefinitionsBuilder` for the shared definition files ([record 6](006-generated-api-shape.md)). `CodeGenerationAdapter` picks them from two maps. There is no common base class; what the builders share is the model they render.

**Every language is rendered from FreeMarker templates.** The builders map the `FlowGraph` and the shared definitions (`SharedDefinitionsGraph`, computed once like the flow graph) to a template model whose strings are finished source: literals escaped, names escaped or disambiguated, references qualified. The templates under `templates/<language>` only place them, so they read like the code they produce. `TemplateRenderer` holds one fixed FreeMarker configuration (locale, number and boolean format, line endings), so the same model renders to the same bytes on every machine. Kotlin and Java type references go through `TypeReferences`, which records the imports a file needs and writes a type fully qualified where a generated class of the same simple name would shadow it. Kotlin string literals escape `$`, so engine expressions such as `${applicationId}` are never read as string templates, and names that are Kotlin keywords are put in backticks. C# takes `packagePath` verbatim as the namespace; TypeScript uses it as a directory only and refers to its runtime types through a `Runtime` namespace import, which no generated name can shadow.

**Every language is built on a runtime library of its own ecosystem** ([record 8](008-runtime-library-and-process-paths.md)). Generated C# names the types of the NuGet package `Miragon.BpmnToCode.Runtime` from `global::`, so no type of the consuming project can shadow them; generated TypeScript imports `@miragon/bpmn-to-code-runtime` as the namespace `Runtime`. Nodes implement the same generic interfaces in every language (successors, interior, boundary host), which is what `ProcessPath` is typed with. Shared definitions carry the runtime types where Kotlin does; TypeScript keeps string literals for names, whose literal types are stricter than a wrapper. C# and TypeScript output are experimental ([record 11](011-compatibility-policy.md)).

**Parity is tested, not assumed.** Every language has expected-output fixtures that are compared byte for byte. Text comparison cannot tell valid code from plausible text, so generated Kotlin and Java are compiled in the core tests and read back by reflection into an API-surface snapshot, which changes only when the API changes, not its formatting. Generated C# is handed to the real compiler with warnings as errors, and generated TypeScript to `tsc` with the strictest settings, as a Node and as a bundler project, each against its runtime library; both tests also check that a `ProcessPath` step the model does not have fails to compile. A drift test keeps the copies of the generated API that the runtime libraries test against identical to the fixtures.

## Consequences

- A change to naming, facets or successors is made once and reaches every language.
- What a file looks like is visible in its template; a mistake in a template shows only when it runs, which is why every template is covered by the fixture and compile tests.
- A new language is two builders and two map entries, plus fixtures.
- The C# and TypeScript compile tests are skipped when no .NET SDK or no `tsc` is installed. A contributor without them can break those languages and only learns it in CI, where both are present.
- The runtime types and `ProcessPath` exist three times: in Kotlin, C# and TypeScript. A change to one has to be made in the others by hand; the fixtures, the compile tests and each runtime's tests over the generated example link them.
- FreeMarker is the only code-writing library among the plugins' dependencies ([record 1](001-product-surfaces-and-distribution.md)).

## Rejected alternatives

- **KotlinPoet and JavaPoet for Kotlin and Java.** They took care of imports, escaping and formatting, but every further language needs templates anyway, and two ways of writing code are one too many. Imports and escaping are small, tested pieces of the builders now; the formatting of the generated code is not a contract ([record 11](011-compatibility-policy.md)).
- **Runtime types inlined into every C# file, and a `Runtime.ts` written per TypeScript run.** Nothing to publish, but C# types of two Process APIs were unrelated, and neither language could have `ProcessPath`, which is logic and does not belong into every generated file.
- **C# with constants only and no navigation.** It made the output differ per language in shape, not only in syntax.
