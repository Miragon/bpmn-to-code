# 7. Output languages: `FlowGraph` and FreeMarker templates

## Context

The generator emits the same API in Kotlin, Java and C#. The three languages differ in syntax, in how a singleton or a nested type is written, and in what tooling exists on the JVM to produce their source. What a node is called, which facets it has and which elements follow it does not differ, and must not: an API that means something else per language cannot be documented once.

## Decision

**What is language-independent is computed once.** Before any source is written, `CodeGenerationAdapter` turns a process model into a `ProcessApiGraph`: the API's name, package, process id and engine, plus a `FlowGraph`, a flat, language-neutral list of nodes with their generated names, their facets and their successors (package `codegen/flow`). The shared definitions of a run become a `SharedDefinitionsGraph`, one group of constants per kind (package `codegen/definitions`). The adapter also names and wraps the files. The builders only render a graph to source text.

**One generator per language.** Each `OutputLanguage` has a `LanguageGenerator`: its file extension, a `ProcessApiBuilder` for a Process API and a `SharedDefinitionsBuilder` for one kind of shared definition ([record 6](006-generated-api-shape.md)). `CodeGenerationAdapter` picks it from one map. There is no common base class; what the builders share is the model they render.

**Every language is rendered from FreeMarker templates.** The templates live under `templates/<language>`, so a generated file looks like its template. The builders map a graph to a template model of literals, identifiers and references, and the template writes the code around them; escaping and keywords are handled per language by `KotlinSyntax`, `JavaSyntax` and `CSharpSyntax`, imports and shadowed names for Kotlin and Java by `TypeReferences`. `TemplateRenderer` pins locale, time zone, encoding and number formats, so the same model renders to the same bytes on every machine. The C# namespace is `packagePath` with each segment in PascalCase.

**C# builds on a runtime package.** The types the JVM languages get from the runtime library ([record 8](008-runtime-library-and-process-paths.md)) are hand-written C# in `Miragon.BpmnToCode.Runtime` (`packages/nuget`), published in the generator's version. The generated code names them with `global::`, so no type or namespace of the consumer can shadow them. `Miragon.BpmnToCode.MSBuild` generates during `dotnet build` through the .NET tool of its own version and brings the runtime package of that version, so generator and runtime come in the same version. C# output is stable since 6.6.0; only the .NET tool shares the experimental status of the command-line tool ([record 11](011-compatibility-policy.md)).

**Parity is tested, not assumed.** Every language has expected-output fixtures that are compared byte for byte. Text comparison cannot tell valid code from plausible text, so generated Kotlin is compiled in the core tests, and generated C# is handed to the real compiler with warnings as errors. A drift test keeps the copy of the generated API that the runtime module tests against identical to the fixtures.

## Consequences

- A change to naming, facets or successors is made once and reaches all three languages.
- A new language, or a new way of writing an existing one, is two builders and one map entry, plus fixtures. The golden tests go through the adapter, so they test whatever is registered.
- The C# compile test is skipped when no .NET SDK is installed. A contributor without one can break C# and only learns it in CI, where the SDK is present.
- The runtime types exist twice: as hand-written Kotlin and as hand-written C# in the runtime package. A change to one has to be made in the other by hand; the compile tests build the generated C# against the package sources of the same commit.
- The process-path API ([record 8](008-runtime-library-and-process-paths.md)) exists twice, in Kotlin and in the C# runtime package; the same bike-leasing scenarios run against both.
- A C# consumer needs the runtime package in the generator's version. The MSBuild package ensures that; with the bare tool it is the consumer's job.
- FreeMarker is part of the plugins' dependencies ([record 1](001-product-surfaces-and-distribution.md)).
- Templates are strings: a missing import or a broken escape is not caught by a type tree, only by the golden files and the compile tests.

## Rejected alternatives

- **KotlinPoet and JavaPoet for Kotlin and Java, a text writer for C#.** The poets handle imports, escaping and formatting, but leave three ways of writing code, each with its own layout, and wrap lines on their own. They were used before 6.0 and replaced by the templates.
- **The C# runtime types inlined into every Process API as a nested `Runtime` class.** No package to install, but every file got its own unrelated copy: a shared library could not type against `IFlowNode` or take a `ProcessPath`, and a fix in the path API needed a regeneration instead of a package update.
- **C# with constants only and no navigation.** It made the output differ per language in shape, not only in syntax.
