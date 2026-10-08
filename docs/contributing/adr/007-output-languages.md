# 7. Output languages: `FlowGraph`, poet builders and the C# text writer

## Context

The generator emits the same API in Kotlin, Java and C#. The three languages differ in syntax, in how a singleton or a nested type is written, and in what tooling exists on the JVM to produce their source. What a node is called, which facets it has and which elements follow it does not differ, and must not: an API that means something else per language cannot be documented once.

## Decision

**What is language-independent is computed once.** Before any source is written, a process model is turned into a `FlowGraph`: a flat, language-neutral list of nodes with their generated names, their facets and their successors. It lives in the `codegen/flow` package. The builders only render it.

**One pair of builders per language.** Each `OutputLanguage` has a `ProcessApiBuilder` for a Process API and a `SharedDefinitionsBuilder` for the shared definition files ([record 6](006-generated-api-shape.md)). `CodeGenerationAdapter` picks them from two maps. There is no common base class; what the builders share is the model they render.

**Kotlin and Java are rendered with KotlinPoet and JavaPoet.** The libraries model a type tree and take care of imports, escaping and formatting.

**C# is rendered by a small text writer.** No comparable library exists on the JVM, so `CSharpWriter` accumulates source text with block-aware indentation, and that is all the C# builders use. `packagePath` is taken verbatim as the namespace.

**C# has no runtime package.** The types the JVM languages get from the runtime library ([record 8](008-runtime-library-and-process-paths.md)) are emitted as one fixed block into every generated C# file, nested in the Process API as `Runtime`. C# output is experimental ([record 11](011-compatibility-policy.md)).

**Parity is tested, not assumed.** Every language has expected-output fixtures that are compared byte for byte. Text comparison cannot tell valid code from plausible text, so generated Kotlin is compiled in the core tests, and generated C# is handed to the real compiler with warnings as errors. A drift test keeps the copy of the generated API that the runtime module tests against identical to the fixtures.

## Consequences

- A change to naming, facets or successors is made once and reaches all three languages.
- A new language is two builders and two map entries, plus fixtures.
- The C# compile test is skipped when no .NET SDK is installed. A contributor without one can break C# and only learns it in CI, where the SDK is present.
- The runtime types exist twice: as hand-written Kotlin and as a C# text block. A change to one has to be made in the other by hand, and nothing but the fixtures links them.
- Runtime types of two generated C# files are unrelated to each other, so generic C# code over several Process APIs needs its own abstraction. The process-path API ([record 8](008-runtime-library-and-process-paths.md)) has no C# counterpart.
- The poet libraries are part of the plugins' dependencies ([record 1](001-product-surfaces-and-distribution.md)).

## Rejected alternatives

- **One template engine for all languages.** For Kotlin and Java it gives up what the poet libraries do for free: imports, escaping, and a type tree instead of strings. For C# text emission is what happens, out of necessity and kept to one small class.
- **A NuGet package with the C# runtime types.** The clean long-term answer. Publishing and versioning a second ecosystem's package is a larger effort than the generator, and inlining can be reversed later.
- **C# with constants only and no navigation.** It made the output differ per language in shape, not only in syntax.
