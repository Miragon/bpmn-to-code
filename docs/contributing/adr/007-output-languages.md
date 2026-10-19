# 7. Output languages: `FlowGraph`, poet builders and text writers

## Context

The generator emits the same API in Kotlin, Java, C# and TypeScript. The languages differ in syntax, in how a singleton or a nested type is written, and in what tooling exists on the JVM to produce their source. What a node is called, which facets it has and which elements follow it does not differ, and must not: an API that means something else per language cannot be documented once.

## Decision

**What is language-independent is computed once.** Before any source is written, a process model is turned into a `FlowGraph`: a flat, language-neutral list of nodes with their generated names, their facets and their successors. It lives in the `codegen/flow` package. The builders only render it.

**One pair of builders per language.** Each `OutputLanguage` has a `ProcessApiBuilder` for a Process API and a `SharedDefinitionsBuilder` for the shared definition files ([record 6](006-generated-api-shape.md)). `CodeGenerationAdapter` picks them from two maps. There is no common base class; what the builders share is the model they render.

**Kotlin and Java are rendered with KotlinPoet and JavaPoet.** The libraries model a type tree and take care of imports, escaping and formatting.

**C# is rendered by a small text writer.** No comparable library exists on the JVM, so `CSharpWriter` accumulates source text with block-aware indentation, and that is all the C# builders use. The namespace is `packagePath` with each segment in PascalCase.

**TypeScript is rendered the same way.** `TypeScriptWriter` is a text writer like `CSharpWriter`, and the API has the shape of the Kotlin one: one exported singleton class per element, whose `next`, `startEvents` and `attachedTo` are getters that return the other classes' instances, so the module loads whatever the order of its classes. The shared definitions are `as const` objects, so every value keeps its literal type. A further language is written in Kotlin as well, never from templates, because the writers then stay compiler-checked and add no template engine to the users' build ([record 14](014-command-line-tool.md)).

**C# and TypeScript build on runtime packages.** The types the JVM languages get from the runtime library ([record 8](008-runtime-library-and-process-paths.md)) are hand-written in `Miragon.BpmnToCode.Runtime` (`packages/nuget`) and `@miragon/bpmn-to-code-runtime` (`packages/npm`), published in the generator's version. A library can then type against `FlowNode` or take a `ProcessPath` whatever Process API it is used with, and a fix reaches consumers as a package update. Generated C# names the types with `global::`, so no type or namespace of the consumer can shadow them; generated TypeScript imports the package as `Runtime`. The version has to match the generator: `Miragon.BpmnToCode.MSBuild` generates during `dotnet build` through the .NET tool of its own version and brings the runtime of that version, and `@miragon/bpmn-to-code` declares the runtime as a peer dependency of exactly its own version, so npm refuses another one. C# and TypeScript output are experimental ([record 11](011-compatibility-policy.md)).

**Parity is tested, not assumed.** Every language has expected-output fixtures that are compared byte for byte. Text comparison cannot tell valid code from plausible text, so generated Kotlin is compiled in the core tests, generated C# is handed to the real compiler with warnings as errors, and generated TypeScript to `tsc` in strict mode under both `nodenext` and `bundler` resolution. A drift test keeps the copy of the generated API that the runtime module tests against identical to the fixtures.

## Consequences

- A change to naming, facets or successors is made once and reaches every language.
- A new language is two builders and two map entries, plus fixtures.
- The TypeScript compile test is skipped until `npm ci` has run in `bpmn-to-code-core/src/test/typescript`, which CI does.
- The C# compile test is skipped when no .NET SDK is installed. A contributor without one can break C# and only learns it in CI, where the SDK is present.
- The runtime types exist three times: hand-written in Kotlin, C# and TypeScript. A change to one has to be made in the others by hand; the compile tests build the generated C# and TypeScript against the package sources of the same commit.
- The process-path API ([record 8](008-runtime-library-and-process-paths.md)) exists three times as well; the same bike-leasing scenarios run against all three.
- Each ecosystem has one more package to publish. Consumers need the runtime in the generator's version; the MSBuild package and the npm peer dependency enforce it, with the bare .NET tool it is the consumer's job.
- The poet libraries are part of the plugins' dependencies ([record 1](001-product-surfaces-and-distribution.md)).

## Rejected alternatives

- **One template engine for all languages.** For Kotlin and Java it gives up what the poet libraries do for free: imports, escaping, and a type tree instead of strings. For C# text emission is what happens, out of necessity and kept to one small class.
- **The runtime types written into every run, as `Runtime.cs` and `Runtime.ts`.** No package to install, but two runs got unrelated copies: a shared library could not type against `FlowNode` or take a `ProcessPath` (in TypeScript the private members of `ProcessPath` make two copies incompatible), and a fix in the path API needed a regeneration instead of a package update.
- **TypeScript as `as const` data that names its successors by key.** An object literal cannot point at its siblings, so `next` would hold `FlowNodes` keys and a generic helper would turn them into a typed chain by casting. The shape then differed from every other language, and the helper's types were harder to read than the generated classes.
- **C# with constants only and no navigation.** It made the output differ per language in shape, not only in syntax.
