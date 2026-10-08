# ADR 023: One Process API per BPMN File

## Status
Accepted — supersedes [ADR 002](002-model-merging.md).

## Context

[ADR 002](002-model-merging.md) merged BPMN files sharing a `processId` into one model, so that a process
existing in several variants (per location, per environment) got a single API. Since 6.0 that was opt-in behind
`enableVariants`, and the merged API kept each file's navigation apart under `FlowVariants.<Variant>`.

The merge did not earn its keep:

- **The generated code hardly used it.** Every variant already rendered a complete `FlowNodes` of its own. The
  merged union only fed the shared definitions, the second validation pass and the JSON export.
- **It was wrong by construction.** A node declared differently in two files took its attributes from the file
  whose variant name sorted first, silently. A job type that only a later variant declared could be referenced
  by that variant's node without ever being generated.
- **It kept breaking.** Six bug fixes and four reworks in about ten months, plus a validation pipeline split
  into a pre-merge and a post-merge phase that custom rules had to know about.
- **It did not reach every entry point.** The validation tasks and the testing module had no `enableVariants`
  and always merged.

## Decision

**Every BPMN file is generated as a Process API of its own. Nothing is merged.**

1. **The name comes from the model.** An API is named `<Variant><ProcessId>ProcessApi`. The variant is the
   optional process extension property `variantName`, the one that named a variant of a merged model before; a
   file without it keeps `<ProcessId>ProcessApi`. `ProcessModel.apiName` is the one place the name is derived,
   and the JSON export uses it too (`<variantName>_<processId>.json`).
2. **Names must be distinct.** Before anything is generated, `SourcedProcessModel.requireDistinctArtifactNames`
   rejects files that would end up under one name and names them with their path relative to the search
   directory. That covers a `processId` declared twice as well as two ids that only collide once generated
   (`foo-bar`, `foo_bar`), which used to drop one API silently. A variant name has to start with a letter and
   may contain letters, digits, `_` and `-`, since it becomes part of a type name and a file name.
3. **No setting in any entry point.** `enableVariants` is removed from Gradle, Maven, the web request and the
   core plugins. Declaring a `variantName` is the opt-in.
4. **Validation has no phases.** Single-model rules run on every file; cross-model rules run once no
   single-model rule reports an error. `ValidationPhase` and `SingleModelValidationRule.phase` are removed: they
   were never documented, and only a rule written for merged models had a reason to set them. The validation
   tasks accept files sharing a `processId` and validate each on its own.
5. **Models are still normalized.** `ProcessModel.normalized()` keeps what merging did for a single file:
   duplicate ids are dropped and every scope and registry is sorted, so output stays a function of the model.

## Consequences

### Positive
- The model set of a run is simply the list of files. `ProcessModel` lost `variants`, `Variant`, `isMerged`
  and `mergeByProcessId`; `FlowScope`, `RootElements` and `FlowNodeDefinition` lost their merge functions.
- One mechanism works identically in Gradle, Maven, the web UI, the validation tasks and the testing module,
  because it is data in the model rather than configuration of a plugin.
- The shared definitions hold what every file really declares.
- A file's API name depends on that file alone, so adding a second file never renames an existing API.

### Negative
- The naming rule lives in the BPMN model and is not visible in the build script.
- Every file carrying a `variantName` is named after it, also one that shares its `processId` with no other
  file. A model that should keep the plain name must not carry one.
- There is no common roof: files sharing a `processId` each carry the same `PROCESS_ID`, and code written
  against several of them names each API.
- `CrossModelValidationContext.findProcess` answers with the first file of a `processId`; a rule that has to
  look at all of them reads `models` itself.
- Projects that used `enableVariants` have to migrate their models and references (see the
  [v6 migration guide](../../changelog/v6.md#files-sharing-a-process-id-each-get-their-own-api)).

## Alternatives Considered

- **A callback or strategy object naming each file.** A function cannot travel through a Maven `pom.xml` or an
  HTTP request, and as a Gradle task input it is tracked by class identity only. It would have worked in one
  entry point and needed a second mechanism for the others.
- **A name template (`{variantName}_{processId}`).** The tool's first mini-language, with its own error cases,
  and no way to say "the default model keeps the plain name" without a second rule.
- **A switch that enables the `variantName` prefix.** Would keep a stray `variantName` from renaming an API,
  at the price of a setting to plumb through every entry point, and of two that lack one today.
- **A new property key (`apiNamePrefix`).** Would have left every model carrying a `variantName` untouched,
  but adds a second name for what users already know as the variant.
- **A mapping table from variant to prefix.** States the variant list a second time, and takes a different
  shape in each entry point.
