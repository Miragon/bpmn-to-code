# ADR 020: C# Output — Templated Emission, Constants Only

## Status
Accepted

## Context

[ADR 005](005-strategy-pattern-code-generation.md) added output languages behind a strategy map of
per-language builders, and claimed that builders "generate identical API structures with language-specific
syntax". That held while the only targets were Kotlin and Java. Adding C# ([#89](https://github.com/Miragon/bpmn-to-code/issues/89))
breaks the claim in two places, and this ADR records why.

The motivation is a polyglot setup the tool previously stopped short of: the engine is hosted in a Java
service while the workers are .NET. Those workers referenced BPMN ids, message names and job types as
hardcoded strings — exactly the drift `bpmn-to-code` removes on the JVM side.

Two facts about the current codebase shaped the decision:

1. **The generated JVM API is not a bag of strings.** Since typed navigation landed, every section except
   `ServiceTasks` wraps its values in types from `bpmn-to-code-runtime` (`ElementId`, `MessageName`,
   `VariableName.Input`, `BpmnError`, `BpmnTimer`, `InputOutputMapping`, …). `Flow` goes further —
   each node extends `AbstractFlowNode` and implements `HasSuccessors` / `FlowScope`.
2. **`bpmn-to-code-runtime` is a JVM artifact** ([ADR 014](014-shared-bpmn-types.md)) with no C#
   counterpart, and no NuGet publishing pipeline exists.

So a C# target could not mirror the JVM shape without first designing, publishing and versioning a C#
runtime package — a considerably larger effort than the generator itself.

## Decision

Ship C# as a **beta** that emits the **constants sections only**, as a `static class` of `const string`
fields written to a `.cs` file, with `packagePath` used verbatim as the namespace.

- **No runtime dependency.** Every value is a plain `const string`. Multi-field values (errors, timers,
  call-activity mappings) become a nested `static class` of consts rather than a wrapper type. The
  generated file compiles in any project with nothing added to it.
- **`Flow` and `Variants` are not generated.** Both are navigation over the process graph and every
  node of them derives from the JVM runtime. A partial navigation API would not compile, so it is omitted
  rather than half-emitted.
- **Templated emission, scoped to C#.** ADR 005 rejected template-based generation because it loses the
  type safety of the poet libraries. That trade-off was between *available* libraries; for C# there is no
  poet equivalent on the classpath. `CSharpWriter` is a small indent-aware text writer used only by
  `CSharpProcessApiBuilder`; Kotlin and Java keep KotlinPoet / JavaPoet.
- **PascalCase identifiers**, derived from the existing `toUpperSnakeCase()` form so the sanitising already
  done there — stripping expression syntax, collapsing `.` `-` `:`, guarding a leading digit — applies to
  C# too. Because the result always starts with a letter or `_`, it can never collide with a C# keyword.
- **Variable direction moves into documentation.** The JVM APIs encode it in the type
  (`VariableName.Input`); C# carries it in an XML doc comment on each constant, so it still surfaces in
  IntelliSense.

Correctness is gated by compiling the output with the real C# compiler (`CSharpCompilationTest`, warnings
escalated to errors) for all three engines, alongside the usual golden-file fixture. The test skips itself
when no .NET SDK is present so contributors without one can still run the suite; CI installs the SDK.

## Consequences

### Positive
- .NET workers get the same rename-safety the JVM side has, without waiting on a C# runtime package.
- Generated C# has zero dependencies, which makes it trivial to vendor into an existing project.
- The strategy map from ADR 005 absorbed a third language with one map entry; `ApiObjectSelection` and the
  navigation IR needed no change at all.

### Negative
- **Output shape now differs per language**, not just syntax — ADR 005's "identical API structures" no
  longer holds across all three targets.
- No compile-time direction enforcement for variables in C#; a doc comment is advisory.
- `CSharpWriter` produces no AST, so C#-specific validity (unlike KotlinPoet/JavaPoet output) rests on the
  compilation test rather than on the emitter.
- The Web app's "include library sources" affordance is JVM-only and is hidden for C#.

## Alternatives Considered

**Publish a C# runtime package and reach full parity** (Rejected for the beta)
- Requires designing the C# equivalents of 15+ runtime types, a NuGet account, publishing and a versioning
  policy coupled to the generator's. Large enough to be its own effort; deferred as a follow-up.

**Emit the runtime types inline into the generated file** (Rejected)
- Makes each generated file self-contained, but duplicates the types across every generated process and
  amounts to designing the C# runtime informally, with no version to reason about.

**Generate constants but keep `Flow` as plain strings** (Rejected)
- The value of `Flow` is the compiler-verified path; without the typed nodes it degrades to a second,
  differently-shaped copy of `Elements`.
