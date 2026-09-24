# ADR 005: Strategy Pattern for Multi-Language Code Generation

## Status
Accepted

## Context
Plugin must generate type-safe API code in multiple languages (Kotlin, Java) with language-specific syntax, type systems, and conventions. Each language requires different code generation libraries (KotlinPoet, JavaPoet).

## Decision
Use Strategy pattern with language-specific builders:

```kotlin
Map<OutputLanguage, AbstractProcessApiBuilder<*>>
```

Each builder implements code generation for its language using appropriate code generation libraries. Builders share common structure through abstract base class.

## Consequences

### Positive
- **Language isolation**: Kotlin/Java generation logic completely separate
- **Library flexibility**: Each builder uses appropriate code generation tool
- **Extensibility**: New languages (Scala, TypeScript) added via new builders
- **Parallel development**: Language implementations can evolve independently

### Negative
- **Code duplication**: Similar object structures generated differently per language
- **Consistency burden**: Must manually ensure output equivalence across languages
- **Testing overhead**: Each builder requires separate test suite

## Alternatives Considered

**Template-based generation** (Rejected)
- Would reduce duplication via shared templates
- Loses type safety of code generation libraries
- Harder to handle language-specific features

## Implementation
```kotlin
val processApiBuilders = mapOf(
    OutputLanguage.KOTLIN to KotlinProcessApiBuilder(),
    OutputLanguage.JAVA to JavaProcessApiBuilder(),
    OutputLanguage.CSHARP to CSharpProcessApiBuilder(),
)
```

Builders generate identical API structures with language-specific syntax.

> **Amended by [ADR 020](020-csharp-constants-only-output.md) and [ADR 022](022-process-api-typed-projection.md).**
> The C# builder uses templated emission rather than a poet library and inlines the runtime types it needs
> into each generated file; since ADR 022 it emits the same API surface as the JVM builders.
