# ADR 004: Strategy Pattern for Multi-Engine Support

## Status
Accepted

## Context
Different BPMN process engines (Camunda 7, Zeebe) use different XML namespaces, element attributes, and extension mechanisms. Extracting process information requires engine-specific parsing logic.

## Decision
Use Strategy pattern with a registry of engine-specific extractors:

```kotlin
Map<ProcessEngine, EngineSpecificExtractor>
```

Each extractor implements `EngineSpecificExtractor` interface and handles its engine's specifics. The `ExtractBpmnAdapter` selects the appropriate extractor based on the target engine.

## Consequences

### Positive
- **Extensibility**: New engines added without touching the existing ones
- **Separation**: Engine-specific logic isolated in dedicated classes
- **Maintainability**: Changes to one engine don't affect others
- **Clear contract**: Interface defines what extraction must provide

### Negative
- Cannot share parsing logic between similar engines (code duplication)
- Adding common functionality requires updating all extractors

## Implementation
```kotlin
// Interface
interface EngineSpecificExtractor {
    fun extract(file: File): BpmnModel
}

// Registry
val extractors = mapOf(
    ProcessEngine.ZEEBE to ZeebeModelExtractor(),
    ProcessEngine.CAMUNDA_7 to Camunda7ModelExtractor()
)
```

Future engines (e.g., Flowable, jBPM) can be added without touching the existing ones.

### Update (6.0, see ADR 017)

The strategy still holds, but the unit of substitution moved one level down, and the
`EngineSpecificExtractor` interface was dropped with it — an interface with a single implementation is not
a strategy. Reading a BPMN file is now one concrete `ProcessModelReader` that combines the
engine-independent `BpmnStructureReader` and `BpmnDefinitionsReader` with an `EngineDialect`, and the
registry holds the dialects directly:

```kotlin
val dialects = mapOf(
    ProcessEngine.ZEEBE to ZeebeDialect(),
    ProcessEngine.CAMUNDA_7 to CamundaDialect(CAMUNDA_7_NAMESPACE),
    ProcessEngine.OPERATON to CamundaDialect(OPERATON_NAMESPACE),
)
```

This also retires the "Negative" consequences above: parsing logic is now shared by construction, and
common functionality is added once in the readers instead of in every extractor.

A dialect is the largest part of adding an engine, but not the whole of it. `EngineDetector` has to learn
the new namespace, and the registry above needs an entry. Both are in the adapter layer, where engine
knowledge belongs.

Two validation rules also branch on the engine, and they are worth telling apart:

- `EngineMismatchRule` renders a display name (`"Zeebe (Camunda 8)"`). `ProcessEngine` is a domain enum,
  so branching on it inside a domain rule is not a layering problem — the string is this rule's message
  text, nothing more. Lifting it onto the enum was considered and rejected: it has exactly one caller,
  and a hard-coded English label on a public domain type is a presentation decision the model should not
  carry.
- `MissingServiceTaskImplementationRule` prints an engine-specific hint (`"Set camunda:topic …"`). This
  *is* adapter vocabulary in the domain layer. It stays there deliberately. The domain may not depend on
  the adapter, so the hint would have to arrive through `SingleModelValidationContext` — which changes
  the surface every custom rule in `bpmn-to-code-testing` is written against, for two sentences of help
  text. The check itself is engine-independent; only the advice is not.

  **Revisit when a third rule needs engine-specific text.** At that point the duplication is real rather
  than hypothetical, and carrying an engine vocabulary on the validation context pays for itself.
