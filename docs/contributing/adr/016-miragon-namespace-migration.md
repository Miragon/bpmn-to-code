# ADR 016: Migration to the `io.miragon` Namespace

## Status
Accepted (supersedes the coordinates/package naming in [ADR 014](014-shared-bpmn-types.md))

## Context
bpmn-to-code shipped under the personal namespace `io.github.emaarco` — both the Maven group and the Kotlin/Java package root (`io.github.emaarco.bpmn.*`). To host and support the project under the [miragon](https://miragon.io) company brand, it moves to `io.miragon`:

- Maven group `io.github.emaarco` → `io.miragon`
- Gradle plugin id `io.github.emaarco.bpmn-to-code-gradle` → `io.miragon.bpmn-to-code-gradle`
- Package root `io.github.emaarco.bpmn.*` → `io.miragon.bpmn.*`

This is a breaking change for consumers on two surfaces: the coordinates in their build files, and the `io.github.emaarco.bpmn.runtime.*` imports baked into their **generated** Process API code. The decision is how to move without forcing every consumer to migrate in lock-step on upgrade.

## Decision
This repository ([`Miragon/bpmn-to-code`](https://github.com/Miragon/bpmn-to-code)) is the home of the project and publishes **only** `io.miragon`, cut as **3.0.0**. Continuity for consumers still on `io.github.emaarco` is split between one mechanism that lives here and one that lives in the legacy repo:

1. **Runtime types — duplicated `@Deprecated` classes in the new jar (kept here).** The `io.miragon:bpmn-to-code-runtime` jar ships the canonical types at `io.miragon.bpmn.runtime.*` **and** `@Deprecated` copies at `io.github.emaarco.bpmn.runtime.*`. The copies must live in the *new* jar (not a separate bridge module) because the Gradle plugin auto-adds the runtime dependency and now adds the new coordinate — a consumer who bumps the plugin but hasn't regenerated only has the new jar on the classpath, so the old types must travel with it.

   They are **real duplicated classes**, not a Kotlin `typealias`: a `typealias` is invisible to Java, and the types are final `data class`/`enum` (so subclassing is out). Real classes are the only mechanism that keeps both Java and Kotlin generated code compiling. The old and new types are therefore distinct JVM types — consumers regenerate and switch imports together per module. This is the only compatibility surface maintained in this repo; it is removed in 4.0.

2. **Old coordinates and old plugin id — served by the legacy `io.github.emaarco` release (not this repo).** The final `io.github.emaarco` 3.0.0, published from the old [`emaarco/bpmn-to-code`](https://github.com/emaarco/bpmn-to-code) repo, keeps the old Maven coordinates (`io.github.emaarco:bpmn-to-code-{runtime,maven,testing}`) and the old Gradle plugin id resolvable and redirected to `io.miragon`. Because `io.github.emaarco` is a **personally-owned** Central namespace and `io.miragon` is **org-owned** (distinct Central credentials), keeping that publication in the repo that owns the old namespace avoids dragging cross-namespace publishing machinery into this one. **This project therefore ships no relocation module and no deprecated plugin-id wrapper** — it would need a second credential set and module to republish a namespace the old repo already owns.

## Consequences

### Positive
- Consumers can adopt `io.miragon` at their own pace: switch coordinates first (the runtime aliases keep generated code compiling), then regenerate per module.
- Generated code emits the new imports by default; the project is fully on `io.miragon` internally, with no cross-namespace build complexity.
- A clean single-namespace project: one Central credential, one Gradle plugin id, no relocation module.

### Negative
- The in-jar compatibility surface (11 duplicated runtime types) must be maintained until 4.0.
- Old-coordinate continuity depends on the legacy repo's final `io.github.emaarco` 3.0.0 release; this repo does not republish those coordinates. A consumer pinning the old coordinate directly is nudged to migrate only by the `@Deprecated` runtime types once their code touches them. Documented in the [v3 Migration Guide](../../changelog/v3.md).
- The project now lives at `github.com/Miragon/bpmn-to-code`; SCM URLs and POM metadata point there, and the Docker image is `miragon/bpmn-to-code-web`. The original author (Marco Schaeck) is retained as the POM developer with a Miragon organization affiliation.

## Alternatives
- **Hard cut (no compat layer).** Simpler, but breaks every consumer's build and generated code at once. Rejected — the project has published releases in the wild.
- **Publish relocation POMs + a deprecated plugin-id wrapper from this repo (the in-place-migration approach).** Rejected: the old repo already owns and continues to publish the `io.github.emaarco` namespace, so duplicating that machinery here would add a second (personal) Central credential and an extra module to republish coordinates that are already covered — with no benefit to consumers.
- **Legacy bridge modules instead of in-jar duplicates.** A separate `io.github.emaarco:bpmn-to-code-runtime` jar holding the old types and depending on the new one. Rejected for the runtime: the plugin auto-adds the *new* coordinate, so the bridge jar wouldn't be on the classpath for plugin users who haven't regenerated.
