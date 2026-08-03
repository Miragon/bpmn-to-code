# ADR 002: Model Merging for Environment Variants

## Status
Accepted

## Context
The same process may exist in different variants across environments (dev vs prod) or organizational units (location A vs location B). While these variants share the same `processId` and core structure, they differ in specific modeling details or execution behavior. Generating separate APIs for each variant would create substantial code duplication since they share the same kernel.

## Decision
Merge BPMN models with identical process IDs into a single unified model:

```kotlin
models.groupBy { it.processId }
  .map { (id, variants) -> mergeModelsWithSameId(id, variants) }
```

All elements from variants are combined using `distinctBy { it.getName() }` to create one comprehensive API containing the superset of all elements across variants.

## Consequences

### Positive
- **Eliminates duplication**: Single API for process regardless of variant count
- **Comprehensive coverage**: Generated API contains all possible elements across environments
- **Simpler integration**: One import instead of environment-specific APIs

### Negative
- **Element conflicts**: If variants define the same element ID differently, one variant's attributes win silently
- **API bloat**: Generated API may include elements not used in all environments

> **Update (2026-08):** Merging is now order-independent. Files are loaded in relative-path order and
> variants are merged in `variantName` order, so the generated output is a deterministic function of
> the `.bpmn` inputs — byte-identical across machines and filesystems. On an element conflict, the
> base attributes come from the variant whose `variantName` sorts first (still ambiguous by design,
> but no longer dependent on filesystem read order).

## Future Improvements
- Detect and warn about element conflicts across variants
- Add variant metadata to generated API documentation
- Support optional variant-specific sub-APIs

## Implementation
Merging occurs in `ModelMergerService` before code generation, ensuring single API per process ID.
