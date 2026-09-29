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
   the camelCase form of its id. A subprocess exposes its interior's start events via `startEvents`. Uniqueness is
   guaranteed model-wide by the mandatory `collision-detection` rule, which now also rejects the same id
   declared in two scopes.
2. **Nodes carry their facets**, mirroring the sealed `FlowNodeDefinition` hierarchy: `id` / `elementType` /
   `name` on all; `JOB_TYPE` on tasks and events with an implementation; `Variables`; `CALLED_PROCESS` with
   `Inputs` / `Outputs` on call activities; `TIMER`; `MESSAGE` / `SIGNAL` / `ERROR` / `ESCALATION`;
   `attachedTo`, `isInterrupting` and `BoundaryEvent<Host>` on boundary events. The `Elements`, `Variables`, `CallActivities` and
   `Timers` sections are removed.
3. **Outgoing sequence flows are named after the element they lead to.** Each node with outgoing flows
   exposes `outgoingFlows` / `OutgoingFlows` with one `to<Element>` entry per target: a
   `SequenceFlow<Target>(id, name, conditionExpression, isDefault, target)`, or a list of them when several
   flows lead to the same element — the name stays stable and no flow is lost. Flow ids are not used for
   names: modelers rarely rename them (`Flow_1csfyyz`), whereas element names already exist and are unique.
   `next` / `Next` stays the element-level view (flows and boundary attachments collapsed by target), so
   existing `ProcessPath` / `PathWalk` steps are unchanged; the additional `via` step walks a chosen sequence
   flow and records it in `flowIds`. Labels are not used either: they are not identifiers and are the most
   volatile part of a model.
4. **Registries stay shared** where BPMN itself models a shared identity: root elements (`Messages`, `Errors`,
   `Signals`, `Escalations`) and job types (`ServiceTasks`, one `const` per distinct type, the canonical
   argument for `@JobWorker`) are the shared definition files of [ADR 021](021-shared-definition-apis.md).
   A node refers to that shared constant instead of repeating the value (`JOB_TYPE = ServiceTasks.X`,
   `message: MessageName = Messages.X`), so a value exists once per run and a node shows where it comes
   from. Only a value no root element declares — an unresolved reference — is written on the node itself.
5. **C# reaches parity by inlining its runtime.** The runtime types are emitted into every generated file
   as a nested `Runtime` class, so the file still has no dependencies and two files never clash. Nodes are
   sealed singletons (`Flow.X.Instance`) navigated by instance, because static members cannot chain;
   `JobType` stays a `const`. This reverses ADR 020's rejection of inlining: with the type set this small and
   no NuGet pipeline, a package would cost more than the duplication.
6. **Member names follow JSON v2** (`conditionExpression`, `isInterrupting`, `isDefault`); references that
   hold the resolved value drop the `Ref` suffix (`attachedTo`, `CALLED_PROCESS`).
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
  registries, per node for variables and call-activity mappings.

### Negative
- Breaking for 5.x consumers: `Elements.X` → `Flow.X.id`, `Variables.Node.V` → `Flow.Node.Variables.V`,
  `CallActivities.Node.*` → `Flow.Node.*`, `Timers.T` → `Flow.T.TIMER`, nested interior nodes → flat.
  C# consumers lose `const string` element ids (`Flow.X.Instance.Id.Value` is an instance property).
- Longer generated files, C# in particular (the runtime block repeats per file).
- Per-file C# runtime types are unrelated across processes; generic .NET tooling needs its own abstraction.

## Alternatives Considered

- **Keep the sections and add `Flows` only.** Leaves the duplication in place and every element named three
  times.
- **Name outgoing flows after their flow id** (the first 6.0 draft). Consistent, but unreadable with the
  modeler's default ids, and a condition could only be found by knowing its flow id.
- **One holder mixing successors and sequence flows.** Autocomplete mixes both kinds, and a flow picked in
  `then { … }` does not compile.
- **`Next` returning transitions instead of elements** (`SequenceFlow` / `BoundaryAttachment`, named after the
  target). BPMN-faithful, but Java and C# need `.getTarget()` / `.Target` on every chained step, `then().x`
  stops being `Flow.X`, and several flows to one target need a third type or a renaming special case. Evaluated
  against eight user personas together with the options above; the target-named `OutgoingFlows` next to an
  unchanged `Next` won clearly.
- **Label-based names.** Prettier for `Yes` / `No`, but needs a sanitiser, breaks on relabelling and is
  non-local (adding a second `No` renames the first).
- **C# NuGet runtime.** The clean long-term answer; deferred because publishing and versioning it is a larger
  effort than the generator, and inlining is reversible.
