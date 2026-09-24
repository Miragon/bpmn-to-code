# ADR 022: Process API as a Typed Projection of the Process Model

## Status
Accepted — supersedes the section layout of [ADR 003](003-generated-api-structure.md) (its naming rules still
apply) and [ADR 020](020-csharp-constants-only-output.md); builds on [ADR 021](021-shared-definition-apis.md).

## Context

Up to 5.x the generated Process API followed two organising principles at once. `Elements`, `Variables`,
`CallActivities` and `Timers` were per-element trees that each re-derived the element naming, while `Flow`
([#97](https://github.com/Miragon/bpmn-to-code/pull/97)) was the only section modelling the process shape.
The registries (`Messages`, `Errors`, `Signals`, `Escalations`, `ServiceTasks`) were a third kind: lists keyed
by a shared identity rather than by element.

Sequence flows were lost entirely on the code side: the navigation graph reduced each flow to its target
node, so id, label, `conditionExpression` and `isDefault` existed only in the [JSON export](018-process-json-v2.md).
A test could not ask "which condition guards this edge" without parsing JSON.

C# emitted constants only ([ADR 020](020-csharp-constants-only-output.md)), because `Flow` needed runtime
types that existed as a JVM artifact and nothing else.

## Decision

The generated code becomes the typed projection of the JSON v2 model: **everything JSON hangs on a
`flowNode` hangs on a node of `Flow`; everything under `definitions` stays a shared registry.**

1. **`Flow` is flat.** Every element, whatever its subprocess depth, is a direct child of `Flow`, addressed by
   the camelCase form of its id. A subprocess keeps `start()` for its interior's start events. Uniqueness is
   guaranteed model-wide by the mandatory `collision-detection` rule, which now also rejects the same id
   declared in two scopes.
2. **Nodes carry their facets**, mirroring the sealed `FlowNodeDefinition` hierarchy: `id` / `elementType` /
   `name` on all; `JOB_TYPE` on tasks and events with an implementation; `Variables`; `calledProcess` with
   `Inputs` / `Outputs` on call activities; `timer`; `message` / `signal` / `error` / `escalation`;
   `attachedTo` and `isInterrupting` on boundary events. The `Elements`, `Variables`, `CallActivities` and
   `Timers` sections are removed.
3. **Sequence flows are typed edges.** Each node with outgoing flows exposes `flows()` / `Flows`, one
   `SequenceFlow<Target>(id, name, conditionExpression, isDefault, target)` per flow, named after the
   **flow id**. `then()` / `Next` stays the node-level view (flows and boundary attachments collapsed by target),
   so `ProcessPath` / `PathWalk` are unchanged. Edge names come from ids, not labels: labels such as
   `"> 3 days"` are not identifiers, are the most volatile part of a model, and a label-based fallback would
   make one edge's name depend on its siblings.
4. **Registries stay shared** where BPMN itself models a shared identity: root elements (`Messages`, `Errors`,
   `Signals`, `Escalations`) and job types (`ServiceTasks`, one `const` per distinct type, the canonical
   argument for `@JobWorker`) are the shared definition files of [ADR 021](021-shared-definition-apis.md).
   A node repeats the same value inline (`message: MessageName`).
5. **C# reaches parity by inlining its runtime.** The runtime types are emitted into every generated file
   as a nested `Runtime` class, so the file still has no dependencies and two files never clash. Nodes are
   sealed singletons (`Flow.X.Instance`) navigated by instance, because static members cannot chain;
   `JobType` stays a `const`. This reverses ADR 020's rejection of inlining: with the type set this small and
   no NuGet pipeline, a package would cost more than the duplication.
6. **Member names follow JSON v2** (`conditionExpression`, `isInterrupting`, `isDefault`); references that
   hold the resolved value drop the `Ref` suffix (`attachedTo`, `calledProcess`).
7. **One reserved-name rule.** An element whose generated name would shadow a holder (`Flow`, `Next`,
   `Instance`, …), a runtime type or a `java.lang.Object` method breaks compilation in at least one language,
   so the mandatory `reserved-element-name` rule rejects it explicitly rather than each language renaming
   silently. C#-only CS0542 cases (a facet named like its node) keep the existing `_` suffix.

## Consequences

### Positive
- One tree instead of five parallel ones; a node's data is where its id is.
- Conditions and default markers are readable and assertable in code, in all three languages.
- C# ships the full API, without a runtime package to publish and version.
- Collision detection mirrors the generated scopes exactly: model-wide for nodes, run-wide for the shared
  registries, per node for variables, sequence flows and call-activity mappings.

### Negative
- Breaking for 5.x consumers: `Elements.X` → `Flow.X.id`, `Variables.Node.V` → `Flow.Node.Variables.V`,
  `CallActivities.Node.*` → `Flow.Node.*`, `Timers.T` → `Flow.T.timer`, nested interior nodes → flat.
  C# consumers lose `const string` element ids (`Flow.X.Instance.Id.Value` is an instance property).
- Longer generated files, C# in particular (the runtime block repeats per file).
- Per-file C# runtime types are unrelated across processes; generic .NET tooling needs its own abstraction.

## Alternatives Considered

- **Keep the sections and add `Flows` only.** Leaves the duplication in place and every element named three
  times.
- **`Next` returning edges instead of nodes.** One access path, but it rewrites `ProcessPath` / `PathWalk` and
  makes boundary attachments a second edge kind; rejected in favour of the additive `Flows` holder.
- **Label-based edge names.** Prettier for `Yes` / `No`, but needs a sanitiser, breaks on relabelling and is
  non-local (adding a second `No` renames the first); id naming is the rule nodes already use.
- **C# NuGet runtime.** The clean long-term answer; deferred because publishing and versioning it is a larger
  effort than the generator, and inlining is reversible.
- **`ProcessPath.via { it.flows.x }`.** No engine assertion library consumes sequence-flow ids today and
  `then` already checks the edge; can be added additively later.
