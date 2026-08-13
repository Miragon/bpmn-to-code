# ADR 017: BPMN-Aligned Sealed Domain Model for Flow Nodes

## Status
Accepted

## Context

[ADR 014](014-shared-bpmn-types.md) and the two-axis `BpmnNodeType` refactor gave the domain a clean *identity*
model: a node knows whether it is a gateway, an event or an activity, and which subtype it is. What it does
**not** have is a clean *data* model. Everything a node carries beyond its identity goes through a single field:

```kotlin
data class FlowNodeDefinition(
    val nodeType: BpmnNodeType,
    val properties: FlowNodeProperties = FlowNodeProperties.None,   // ← one-of
    val engineSpecificProperties: Map<String, Any?> = emptyMap(),   // ← untyped grab-bag
    …
)

sealed interface FlowNodeProperties {
    object None; data class ServiceTask(…); data class Timer(…)
    data class CallActivity(…); data class MessageEvent(…); data class SignalEvent(…)
}
```

`FlowNodeProperties` is a **one-of**, and each extractor picks exactly one variant —
`ZeebeModelExtractor.resolveProperties()` resolves service task *before* call activity *before* timer *before*
event. That was adequate while every node had at most one interesting facet. It stopped being adequate with two
concrete feature requests:

- [#73](https://github.com/Miragon/bpmn-to-code/issues/73) — multi-instance loop characteristics, which apply to
  service tasks, user tasks, call activities **and** sub-processes.
- [#74](https://github.com/Miragon/bpmn-to-code/issues/74) — `zeebe:ioMapping`, which per `zeebe-bpmn-moddle`'s
  own `allowedIn` applies to call activities, events, receive tasks, service tasks, sub-processes and user tasks.

Both are **additive facets**: a multi-instance call activity is still a call activity, and a service task with an
I/O mapping is still a service task. A one-of slot cannot hold two facets at once, so neither feature could be
implemented without either dropping data or bolting nullable fields onto the flat `FlowNodeDefinition`.

Two further gaps surfaced while evaluating the model against the OMG BPMN 2.0 metamodel and `bpmn-moddle`
(see [ADR 018](018-process-json-v2.md) for the full comparison):

- **Only one event definition survived.** `resolveEventDefinitionType()` used `firstNotNullOfOrNull`, while BPMN
  allows a catch event to carry several triggers. `bpmn-moddle` models this as
  `CatchEvent.eventDefinitions` — a *list*.
- **Nesting was a reference, not a structure.** `parentId` on the node, and nothing at all on
  `SequenceFlowDefinition`, so a flow inside a sub-process was indistinguishable from a top-level one.

## Decision

Replace the identity axis (`BpmnNodeType`) plus one-of data axis (`FlowNodeProperties`) with a **single sealed
hierarchy that mirrors the BPMN class tree**, where each subtype carries exactly the facets BPMN permits on it.

```
sealed interface FlowNodeDefinition          // id, name, incoming[], outgoing[], documentation, extensions[]
├── Gateway(kind: GatewayKind, default: String?)
├── Event(shape, eventDefinitions[], attachedToRef?, cancelActivity?, isInterrupting?, ioMapping?)
├── sealed interface Activity                // multiInstance?, ioMapping?, boundaryEventRefs[],
│   │                                        // isForCompensation, default?
│   ├── Task(kind: TaskKind, implementation: TaskImplementation)
│   ├── SubProcess(kind: SubProcessKind, flowNodes[], sequenceFlows[])
│   └── CallActivity(calledElement: CalledElement?)
└── Unknown
```

This keeps the property that made `BpmnNodeType` worth having — **invalid combinations are unrepresentable** —
and extends it from identity to data. A multi-instance gateway, a `calledElement` on an event, or a
`cancelActivity` flag on a task cannot be constructed.

### Containment replaces `parentId`

`Activity.SubProcess` owns its children **and its own sequence flows**, mirroring
`bpmn:FlowElementsContainer.flowElements`. Scope becomes structural instead of inferred, which is what the JSON
contract needs (ADR 018) and what makes an event sub-process or transaction boundary meaningful.

The container has a name, `FlowScope`, but the models do not store it:

```kotlin
data class FlowScope(
    val flowNodes: List<FlowNodeDefinition> = emptyList(),
    val sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
)
```

`ProcessModel`, `ProcessModel.Variant` and `Activity.SubProcess` each name their two halves as separate
fields — that is the shape consumers read. `FlowScope` is the shape the pair takes while it is being
*produced or transformed*: `BpmnStructureReader.read()` returns one instead of holding the halves as state,
and `ModelMergerService` merges and sorts one. Before it had a name, those two had independently grown their
own private `Scope` DTO for exactly this.

Because merging, validation, collision detection and the code builders all reason over a flat node set,
`ProcessModel` exposes a derived DFS-flattened view:

```kotlin
val allFlowNodes: List<FlowNodeDefinition>   // depth-first, containers before their children
fun parentIdOf(nodeId: String): String?      // derived from the tree
```

The tree is the store; the flat list is a projection. This mirrors how `bpmn-js` keeps a nested moddle tree
alongside a flat `ElementRegistry`.

### Typed facets replace the untyped maps

| Was | Now |
|---|---|
| `FlowNodeProperties.ServiceTask` + `ServiceTaskDefinition.engineSpecificProperties["implementationValue"]` | `TaskImplementation` — sealed: `JobWorker`, `Connector`, `ExternalTask`, `JavaClass`, `DelegateExpression`, `Expression`, `CalledDecision`, `Script`, `None` |
| `FlowNodeProperties.Timer` / `.MessageEvent` / `.SignalEvent` (one only) | `Event.eventDefinitions: List<EventDefinitionInstance>` — sealed: `Timer`, `Message`, `Signal`, `Error`, `Escalation`, `Compensation`, `Conditional`, `Link`, `Terminate` |
| *(not representable)* | `Activity.multiInstance: MultiInstanceDefinition?` (#73) |
| *(not representable)* | `ioMapping: IoMapping?` on `Activity` and `Event` (#74) |
| `engineSpecificProperties: Map<String, Any?>` | `extensions: List<EngineExtension>` — namespaced `$type`, nested children, body text |
| `TimerDefinition.type: String?` (`"Duration"`) | `TimerType` enum (`DATE`, `DURATION`, `CYCLE`) |

`TaskImplementation` subsumes the three parallel `ZeebeImplementationKind` / `Camunda7ImplementationKind` /
`OperatonImplementationKind` enums: the *kind* and its *payload* were previously split across an enum and a
string map, and are now one value.

`EngineExtension` is a faithful projection of a foreign-namespace XML element —
`$type` (`prefix:localName`), its attributes, its children, its body — the same shape `moddle` produces via
`createAny()`. It is the lossless escape hatch for engine features we have not normalised (and for
[#42](https://github.com/Miragon/bpmn-to-code/issues/42)'s connector properties), and it carries namespace
provenance, which the old flat map did not.

### Root-element registries are keyed correctly

`MessageDefinition`, `SignalDefinition`, `ErrorDefinition` and `EscalationDefinition` previously stored the
**event node's** id, because they were built from `eventDefinition.parentElement`. In BPMN these are
`Definitions.rootElements` referenced by many events. They now key on the real root-element id, and the per-node
reference lives in the corresponding `EventDefinitionInstance` (`messageRef`, `signalRef`, `errorRef`,
`escalationRef`). A message reused by three events is now one registry entry with three references.

### The generated Process API is preserved

`ElementTypeName` is retained. It renders the flat `SERVICE_TASK` / `MESSAGE_START_EVENT` vocabulary for the
*code* API only, now deriving the event subtype from the first entry of `eventDefinitions`. The generated
Kotlin/Java files — including `BpmnRelations.elementType`, `previousElements` and `followingElements` — are
byte-identical before and after this ADR, verified by regenerating every fixture in `shared/bpmn` through
the full pipeline at both commits (see ADR 018).

One deliberate exception was added later: root elements that nothing references used to be filtered out
during extraction, which meant the model quietly disagreed with the file. Filtering moved out of the
extractor into `UnreferencedRootElementRule`, so such a declaration now reaches the model — and produces a
constant — while the rule reports it. Re-running the same fixture comparison isolates the effect to 4 of 16
generated files, each gaining exactly one constant.

## Consequences

### Positive
- #73 and #74 become straightforward field additions on the subtype that BPMN says owns them.
- Multi-trigger events are representable; no event definition is silently dropped.
- Scope is structural, so sequence flows finally know which container they belong to.
- Engine-specific data is namespaced, nested and lossless instead of a flat primitives-only map.
- Extractors get simpler: no priority chain deciding which single facet wins.
- Validation rules can pattern-match on the hierarchy (`is Activity.CallActivity`) instead of casting a
  one-of `properties` field.

### Negative
- Touches most of `bpmn-to-code-core` (~43 files including tests). Contained to that module — the Gradle,
  Maven, web, runtime and testing modules do not reference these types.
- Consumers reading a node now switch on the sealed subtype rather than reading nullable fields — more
  ceremony for simple traversals, which `allFlowNodes` mitigates.
- The tree/flat duality is one more concept to hold. Justified because both shapes have real consumers
  (JSON output needs the tree; merging and codegen need the flat view).

### One model type, not three

`ProcessModel` is a single data class. `BpmnModel` (one file) and `MergedBpmnModel` (several files sharing a
process id) previously split it into a sealed hierarchy, but the two differed only in whether variants were
present — and every consumer asked exactly that, via `is MergedBpmnModel` or `as? BpmnModel`. That is now
`isMerged`, and the three type checks in the builders and the JSON mapper became property reads.

The four `bpmn:Definitions` registries travel together as [RootElements] rather than as four parallel
fields. They were always merged, filtered and sorted as a unit, so the four-way repetition appeared nine
times across the model, the merger, the extractor and the mapper; merging, filtering and sorting now live
on the value object itself.

`BpmnModelApi.engine` is `targetEngine`, distinct from `ProcessModel.detectedEngine`. Both were called
`engine`, so inside a builder `modelApi.engine` and `modelApi.model.engine` meant different things —
selected versus detected — with nothing in the names to say so. Their difference is exactly what
`EngineMismatchRule` reports.

## Known follow-up: `VariableMapping` is a code-generation contract in the domain

Every definition type implements `VariableMapping`, whose `getName()` returns an upper-snake-case **Java
identifier** and whose `getValue()` returns a `Pair<String, String>` for errors and escalations — not
because a BPMN error *is* a pair, but because the generated `BpmnError(name, code)` constructor takes two
arguments. That is a code-generation ABI expressed as a domain interface, and it is why `TimerType.label`
carries the string `"Duration"`.

It was left in place for 6.0 because moving it is not mechanical. `getName()` is consumed by the two API
builders — clearly adapter concerns — but also by `CollisionDetectionService`, which exists to predict
duplicate constant names in the generated API and is itself reachable from a domain validation rule.
Relocating the interface therefore forces a decision on whether "will the generated API collide?" is a
domain rule or an adapter rule. That deserves its own ADR rather than being settled inside a release.

Two consequences to keep in mind meanwhile: `domain/shared/**` is excluded from the coverage gate, so the
name-normalisation logic in these types is unmeasured; and derived projections such as
`ProcessModel.serviceTasks` have to key on the node rather than on the generated name, because collapsing
by name in the domain hides distinct elements from validation.

## Known follow-up: root-element names are still copied onto the node tree

Referencing a root element needs one field — the `…Ref`. The node also carries the name, and for errors and
escalations the code as well:

| `EventDefinitionInstance` | Reference | Copied from the registry entry |
|---|---|---|
| `Message` (via `MessageReference`) | `messageRef` | `messageName` |
| `Signal` | `signalRef` | `signalName` |
| `Error` | `errorRef` | `errorName`, `errorCode` |
| `Escalation` | `escalationRef` | `escalationName`, `escalationCode` |

The **published JSON has none of this** — `EventDefinitionJson` emits only the `…Ref`, and names and codes
appear exactly once, in `definitions`. The duplication is domain-internal.

It remains because the correlation rules match on names rather than ids: `messageUsages()` and
`signalUsages()` build their `NamedEventUsage` from the copied name, and `MissingErrorDefinitionRule` uses
`errorName == null || errorCode == null` to detect a half-configured error — it needs to observe the
*absence* on the node.

Resolving through the registry instead is a contained change (five call sites), but it changes what those
rules mean: after `withReferencedDefinitionsOnly()` a node with a null `…Ref` has nothing to resolve, so
"incomplete definition" needs a new formulation. Worth doing on its own, not folded into a release.

Timers and compensations have no such problem and need no follow-up: BPMN defines neither as a root
element, so their data lives inline on the event definition and `ProcessModel.timers` / `.compensations`
are derived views recomputed from the node tree, which cannot drift.

## Alternatives Considered

**Add nullable `multiInstance` / `ioMapping` fields to the flat `FlowNodeDefinition`.** Smallest change, but it
makes "a multi-instance parallel gateway" constructible and pushes validity checks to runtime — the exact
problem `BpmnNodeType` was introduced to solve.

**Keep `FlowNodeProperties` and add an `Activity` grouping variant carrying `multiInstance`.** Solves #73 alone,
but not #74 (I/O mappings are legal on events too, which the activity grouping cannot express) and not the
multi-trigger event gap. It also keeps the one-of, so a service task with both an implementation and an I/O
mapping still would not fit.

**Model nesting only in the JSON adapter and keep the domain flat.** Rejected: the domain would still be unable
to answer "which scope does this sequence flow belong to", so validation rules and future reachability analysis
([#48](https://github.com/Miragon/bpmn-to-code/issues/48)) would each have to rebuild the tree themselves.
