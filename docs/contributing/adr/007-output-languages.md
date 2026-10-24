# 7. Output languages: `FlowGraph` and FreeMarker templates

## Context

The generator emits the same API in Kotlin, Java, C# and TypeScript. The languages differ in syntax, in how a singleton or a nested type is written, and in what tooling exists on the JVM to produce their source. What a node is called, which facets it has and which elements follow it does not differ, and must not: an API that means something else per language cannot be documented once.

## Decision

**What is language-independent is computed once.** Before any source is written, `CodeGenerationAdapter` turns a process model into a `ProcessApiGraph`: the API's name, package, process id and engine, plus a `FlowGraph`, a flat, language-neutral list of nodes with their generated names, their facets and their successors (package `codegen/flow`). The shared definitions of a run become a `SharedDefinitionsGraph`, one group of constants per kind (package `codegen/definitions`). The adapter also names and wraps the files. The builders only render a graph to source text.

**One generator per language.** Each `OutputLanguage` has a `LanguageGenerator`: its file extension, a `ProcessApiBuilder` for a Process API and a `SharedDefinitionsBuilder` for one kind of shared definition ([record 6](006-generated-api-shape.md)). `CodeGenerationAdapter` picks it from one map. There is no common base class; what the builders share is the model they render. The flow nodes are that model literally: `FlowNodeMapper` builds one `FlowNodeModel` per node for every language, and a language contributes only its `FlowNodeSyntax`, so a new facet is mapped once.

**Every language is rendered from FreeMarker templates.** The templates live under `templates/<language>`, so a generated file looks like its template. The builders map a graph to a template model of literals, identifiers and references, and the template writes the code around them; escaping and keywords are handled per language by `KotlinSyntax`, `JavaSyntax`, `CSharpSyntax` and `TypeScriptSyntax`, imports and shadowed names for Kotlin and Java by `TypeReferences`, imports for TypeScript by `TypeScriptSourceFile`. `TemplateRenderer` pins locale, time zone, encoding and number formats, so the same model renders to the same bytes on every machine. The C# namespace is `packagePath` with each segment in PascalCase.

**TypeScript has the shape of the Kotlin API.** One exported singleton class per element, whose `next`, `startEvents` and `attachedTo` are getters that return the other classes' instances, so the module loads whatever the order of its classes. The shared definitions are `as const` objects, so every value keeps its literal type. A module imports the runtime package type-only unless it uses one of its values, and the shared definition files it refers to, with a `.js` extension that resolves under both `nodenext` and `bundler`.

**C# and TypeScript build on runtime packages.** The types the JVM languages get from the runtime library ([record 8](008-runtime-library-and-process-paths.md)) are hand-written in `Miragon.BpmnToCode.Runtime` (`packages/nuget`) and `@miragon/bpmn-to-code-runtime` (`packages/npm`), published in the generator's version. A library can then type against `FlowNode` or take a `ProcessPath` whatever Process API it is used with, and a fix reaches consumers as a package update. Generated C# names the types with `global::`, so no type or namespace of the consumer can shadow them; generated TypeScript imports the package as `Runtime`. The version has to match the generator: `Miragon.BpmnToCode.MSBuild` generates during `dotnet build` through the .NET tool of its own version and brings the runtime of that version, and `@miragon/bpmn-to-code` declares the runtime as a peer dependency of exactly its own version, so npm refuses another one. C# and TypeScript output are experimental until their packages are published ([record 11](011-compatibility-policy.md)).

**Parity is tested, not assumed.** Every language has expected-output fixtures that are compared byte for byte. Text comparison cannot tell valid code from plausible text, so generated Kotlin and Java are compiled in the core tests. Generated C# and TypeScript are compiled next to their runtime packages, on code the command-line tool generates: C# with warnings as errors in `packages/nuget`, TypeScript with `tsc` in strict mode under both `nodenext` and `bundler` resolution in `packages/npm`. A drift test keeps the copy of the generated API that the runtime module tests against identical to the fixtures.

## Consequences

- A change to naming, facets or successors is made once and reaches every language.
- A new language, or a new way of writing an existing one, is two builders and one map entry, plus fixtures. The golden tests go through the adapter, so they test whatever is registered.
- `./gradlew build` does not compile C# or TypeScript. A contributor without a .NET SDK or Node can break either and only learns it in CI, where `build-packages.yml` runs both.
- The runtime types exist three times: hand-written in Kotlin, C# and TypeScript. A change to one has to be made in the others by hand; the package tests build the generated C# and TypeScript against the package sources of the same commit.
- The process-path API ([record 8](008-runtime-library-and-process-paths.md)) exists three times as well; the same bike-leasing scenarios run against all three.
- Each ecosystem has one more package to publish. Consumers need the runtime in the generator's version; the MSBuild package and the npm peer dependency enforce it, with the bare .NET tool it is the consumer's job.
- FreeMarker is part of the plugins' dependencies ([record 1](001-product-surfaces-and-distribution.md)).
- Templates are strings: a missing import or a broken escape is not caught by a type tree, only by the golden files and the compile tests.

## Rejected alternatives

- **KotlinPoet and JavaPoet for Kotlin and Java, a text writer for C#.** The poets handle imports, escaping and formatting, but leave three ways of writing code, each with its own layout, and wrap lines on their own. They were used before 6.0 and replaced by the templates.
- **The runtime types inlined into every generated file.** No package to install, but every file got its own unrelated copy: a shared library could not type against `FlowNode` or take a `ProcessPath` (in TypeScript the private members of `ProcessPath` make two copies incompatible), and a fix in the path API needed a regeneration instead of a package update.
- **TypeScript as `as const` data that names its successors by key.** An object literal cannot point at its siblings, so `next` would hold `FlowNodes` keys and a generic helper would turn them into a typed chain by casting. The shape then differed from every other language, and the helper's types were harder to read than the generated classes.
- **C# with constants only and no navigation.** It made the output differ per language in shape, not only in syntax.
