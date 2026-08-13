# ADR 018: Process JSON v2 — a BPMN-Standard-Aligned Public Contract

## Status
Accepted — supersedes [ADR 012](012-json-export.md)

## Context

[ADR 012](012-json-export.md) introduced the JSON export as a second output format for AI assistants and for
reviewing process changes as text. It has since grown feature by feature and is about to be treated as a
**stable, public contract**. [Issue #58](https://github.com/Miragon/bpmn-to-code/issues/58) asked to evaluate it
against established non-XML BPMN domain models before locking it down.

### What we compared against

Verified against the actual descriptors — `bpmn-moddle@10`'s `bpmn.json`, `camunda-bpmn-moddle@7`,
`zeebe-bpmn-moddle`, and the element-templates JSON schema. Camunda 8 has no public JSON process-model
representation (deployment is XML), so it is not a usable reference.

| Concern | bpmn-moddle (OMG-derived) | JSON v1 |
|---|---|---|
| Node type | `$type: "bpmn:StartEvent"` + `eventDefinitions: []` — an **array**, multi-trigger events are legal | one flattened string `MESSAGE_START_EVENT`; only one definition survives |
| Nesting | `FlowElementsContainer.flowElements` on `Process` and `SubProcess` | flat list + `parentId`; sequence flows carry no scope at all |
| Relations | `SequenceFlow.sourceRef/targetRef` plus `FlowNode.incoming/outgoing` → **SequenceFlow** ids | node→node `previousElements` / `followingElements` **and** a top-level `sequenceFlows` array |
| Boundary events | `attachedToRef`, `Activity.boundaryEventRefs`, `cancelActivity` (default `true`) | `attachedToRef`, `attachedElements`, `interrupting` |
| Loop characteristics | `Activity.loopCharacteristics` → `MultiInstanceLoopCharacteristics` | absent |
| Root elements | `Definitions.rootElements` — `bpmn:Message` / `Signal` / `Error` / `Escalation`, referenced by `…Ref` | top-level lists keyed by the *event node's* id |
| Extensions | `extensionElements.values[]`, namespaced `$type`, arbitrarily nested; unknown content preserved | flat `Map<String, JsonElement>`, primitives only, no namespace |
| Where an extension is legal | machine-readable `allowedIn` | not modelled |
| Versioning | schema per package + uri | no `$schema`, no format version |

### Concrete defects in v1

1. **Relations encoded three times** (`previousElements`, `followingElements`, `sequenceFlows`), and node→node
   adjacency loses *which* flow was taken — so a condition or default branch cannot be attributed to a target.
2. **`parentId` cannot scope sequence flows.** `SequenceFlowJson` has no scope field;
   `MergedBpmnModel.sequenceFlows` even returns an empty list.
3. **`properties` is a single one-of slot**, so multi-instance ([#73](https://github.com/Miragon/bpmn-to-code/issues/73))
   and `zeebe:ioMapping` ([#74](https://github.com/Miragon/bpmn-to-code/issues/74)) are unrepresentable —
   see [ADR 017](017-bpmn-aligned-domain-model.md).
4. **Only one event definition survives**, though BPMN permits several triggers on one catch event.
5. **The top-level registries are mis-keyed** — `messages[].id` … `compensations[].id` hold the *event node's*
   id, not the `bpmn:Message` / `Signal` / `Error` root-element id, so a message reused by three events appears
   three times and cannot be de-duplicated or resolved.
6. **`compensations[].activityRef` was wrong** — it emitted the compensation event's own id, while the real
   reference sat unused in `engineSpecificProperties`.
7. **`engineSpecificProperties` cannot hold structured data** — any non-primitive was coerced to
   `toString()`, and keys carried no namespace, so C7 and Zeebe keys could collide with no provenance.
8. **Variable direction was dropped**: the generated *code* API splits `Inputs` / `Outputs`
   ([ADR 015](015-directional-variable-extraction.md)), the JSON emitted a flat `List<String>`.
9. **Process metadata already in the domain was discarded** — `isExecutable`, the detected engine, the process
   name.
10. **No `$schema` and no format version**, so consumers could neither pin nor detect a break.
11. **`variants` made one file carry two shapes** — either `flowNodes` or `variants` was populated.
12. **The documentation was already out of sync**, describing `incoming` / `outgoing` while the code emitted
    `previousElements` / `followingElements` — borrowing moddle's field names for different semantics.

## Decision

Publish a **v2 contract** that is structurally aligned with OMG BPMN 2.0 / `bpmn-moddle`, versioned by a
published JSON Schema, and layered so that standard data, normalised cross-engine data and raw engine data are
each in a well-defined place.

### The three layers, per element

1. **BPMN-standard core** — names and shapes taken from the OMG metamodel: `id`, `name`, `type` (the BPMN
   element's local name), containment, `incoming` / `outgoing` (sequence-flow ids), `eventDefinitions[]`,
   `attachedToRef`, `cancelActivity`, `triggeredByEvent`, `boundaryEventRefs`, `default`, `isForCompensation`,
   `documentation`.
2. **Normalised cross-engine facets** — bpmn-to-code's own value-add, identical whether the source was
   `zeebe:*` or `camunda:*`: `implementation`, `ioMapping`, `variables`, `multiInstance`, `calledElement`, and
   the event-definition payloads (`timerType` / `expression`, `subscription.correlationKey`).
3. **Raw engine extensions** — `extensions[]` with a namespaced `$type`, plus `attributes`, nested `children` and `body`, and
   `engineAttributes` for foreign-namespace *attributes*. New engine features show up here without a schema
   change.

   This layer carries what layer 2 does **not**. Anything a dialect reads in full is left out, because
   emitting both would state the same fact twice and let the two drift apart.

   For *elements* that means, in Zeebe, `taskDefinition`, `ioMapping`, `loopCharacteristics` and
   `calledElement`. Partially read elements stay: `camunda:inputParameter` may nest a `camunda:script`
   that `ioMapping` does not carry, so the raw form remains the only way to reach it.

   For *attributes* the same rule applies, but resolved per node rather than as a fixed list.
   `camunda:topic`, `camunda:delegateExpression`, `camunda:class` and `camunda:expression` all map to the
   same `implementation` field and are mutually exclusive by precedence — a model declaring two would see
   only one normalised, so only the winner is dropped and the loser stays reachable. Attributes with no
   typed counterpart (`camunda:asyncBefore`, `camunda:exclusive`, `camunda:type`) always stay.

### Shape

```jsonc
{
  "$schema": "https://miragon.github.io/bpmn-to-code/schema/process-model/2.0.json",
  "formatVersion": "2.0",
  "process": {
    "id": "newsletterSubscription",
    "name": "Newsletter Subscription",
    "isExecutable": true,
    "engine": "ZEEBE",
    "flowNodes": [
      { "id": "StartEvent_SubmitRegistrationForm", "type": "startEvent", "name": "Submit newsletter form",
        "outgoing": ["Flow_1csfyyz"],
        "eventDefinitions": [
          { "type": "message", "messageRef": "Message_1",
            "subscription": { "correlationKey": "=subscriptionId" } }
        ],
        "variables": [ { "name": "subscriptionId", "direction": "OUTPUT" } ] },

      { "id": "SubProcess_Confirmation", "type": "subProcess", "triggeredByEvent": false,
        "incoming": ["Flow_0zdmt0t"], "outgoing": ["Flow_09cuvzp"],
        "boundaryEventRefs": ["ErrorEvent_InvalidMail", "Timer_After3Days"],
        "flowNodes": [
          { "id": "Activity_SendConfirmationMail", "type": "serviceTask",
            "incoming": ["Flow_05i3x1y"], "outgoing": ["Flow_1bckm43"],
            "implementation": { "type": "jobWorker", "jobType": "newsletter.sendConfirmationMail" },
            "ioMapping": { "inputs": [ { "target": "subscriptionId", "source": "=subscriptionId" } ] },
            "multiInstance": { "sequential": true, "inputCollection": "=items", "inputElement": "item" },
            "extensions": [ { "$type": "zeebe:taskHeaders",
                              "children": [ { "$type": "zeebe:header",
                                              "attributes": { "key": "k", "value": "v" } } ] } ] },
          { "id": "Timer_EveryDay", "type": "boundaryEvent", "name": "Every day",
            "attachedToRef": "Activity_ConfirmRegistration", "cancelActivity": false,
            "outgoing": ["Flow_0x4ewvb"],
            "eventDefinitions": [ { "type": "timer", "timerType": "DURATION", "expression": "PT1M" } ] }
        ],
        "sequenceFlows": [
          { "id": "Flow_05i3x1y", "sourceRef": "StartEvent_RequestReceived",
            "targetRef": "Activity_SendConfirmationMail" }
        ] }
    ],
    "sequenceFlows": [
      { "id": "Flow_16hub0n", "sourceRef": "Gateway_SplitNotifications",
        "targetRef": "Activity_SendWelcomeMail", "name": "in stock", "conditionExpression": "=stock > 0" }
    ]
  },
  "definitions": {
    "messages": [ { "id": "Message_1", "name": "Message_FormSubmitted" } ],
    "signals":  [ { "id": "Signal_1", "name": "Signal_RegistrationNotPossible" } ],
    "errors":   [ { "id": "Error_1", "name": "Error_InvalidMail", "errorCode": "500" } ],
    "escalations": []
  }
}
```

### Deltas from v1, and why

| Change | Defect addressed |
|---|---|
| `previousElements` / `followingElements` → `incoming` / `outgoing` holding **sequence-flow ids** | 1, 12 — moddle semantics; the flow carries its own name, condition and default flag, so a branch is finally attributable |
| `parentId` → real containment; each scope owns its `flowNodes` **and** `sequenceFlows` | 2 |
| `properties` one-of → `implementation` + `eventDefinitions[]` + `multiInstance?` + `ioMapping?` | 3, 4, #73, #74 |
| `elementType: "MESSAGE_START_EVENT"` → `type: "startEvent"` + `eventDefinitions[]` | 4 — maps 1:1 onto moddle by prefixing `bpmn:` |
| `interrupting` → `cancelActivity` (boundary) / `isInterrupting` (event sub-process start) | BPMN attribute names |
| `isDefault` on the flow → `default` on the gateway / activity | OMG places it on the source element |
| Registries keyed by root-element id; nodes reference them via `messageRef` / `signalRef` / `errorRef` / `escalationRef` | 5 |
| Compensation becomes a per-node event definition with the real `activityRef` and `waitForCompletion` | 6 |
| `engineSpecificProperties` → `extensions[]` (namespaced, nested) + `engineAttributes` | 7, and unblocks [#42](https://github.com/Miragon/bpmn-to-code/issues/42) |
| `variables: ["x"]` → `[{ name, direction, expression }]` | 8 — parity with the code API |
| Added `process.name`, `process.isExecutable`, `process.engine` | 9 |
| Added `$schema` and `formatVersion` | 10 |
| `flowNodes` / `sequenceFlows` are **always** present (the union for merged models); `variants` is purely additive | 11 |

### Versioning

The schema is published with the documentation at
`https://miragon.github.io/bpmn-to-code/schema/process-model/2.0.json` (source:
`docs/public/schema/process-model/2.0.json`) and referenced from every generated file via `$schema`.
Additive changes bump the minor version and reuse the same document; any breaking change gets a new
major-versioned schema URL, so a consumer can pin exactly what it parses.

### Determinism is preserved

ADR 012's guarantee stands: same BPMN in, byte-identical JSON out. Depth-first ordering now applies **per
scope**; registries and extension entries are emitted in a deterministic order.

### Registries hold only referenced root elements

A BPMN file may declare a `bpmn:Message`, `Signal`, `Error` or `Escalation` that no element points at —
usually left over from editing in a modeler. Those are dropped: `definitions` exists so a node's `…Ref` can
be resolved, and a generated constant for a message nothing listens to is misleading. This matches what v1
surfaced, which only ever saw root elements through the events that referenced them.

### The generated Process API is unaffected

Only the JSON contract breaks. The Kotlin/Java Process API — including `BpmnRelations` — is **byte-identical**.

This was verified end-to-end rather than assumed: the full pipeline (load → extract → merge → generate) was
run over every fixture in `shared/bpmn` for all three engines and both output languages, at this commit and
at the last commit before the refactor, and all 18 generated files matched byte-for-byte.

The expected-API golden files in `bpmn-to-code-core/src/test/resources/api` remain the day-to-day regression
guard. Their constant *ordering* changed in this refactor, because they are generated from a hand-built test
fixture that bypasses the merger; every production path merges first, and merging has always sorted flow
nodes by id, so the emitted order is unchanged for real models. The writers now sort explicitly, which is a
no-op after merging and keeps output deterministic for any caller that skips it.

## Consequences

### Positive
- The output is now mappable 1:1 onto `bpmn-moddle` (prefix `type` with `bpmn:`), so bpmn-io tooling and any
  consumer that already knows BPMN needs no translation table.
- #73 and #74 are covered, and #42 has a place to land without another schema break.
- Engine-specific data is namespaced, nested and lossless, so future engine features do not force schema churn.
- Consumers can pin a schema version and validate mechanically.
- Scoped sequence flows and attributable branch conditions make the file genuinely analysable, not just readable.

### Negative
- **Breaking for every JSON consumer.** Field names, nesting and the event vocabulary all change; there is no
  compatibility shim. Released as **6.0.0** with a migration section in the changelog.
- "Give me every node" now needs recursion (`jq '.. | .flowNodes? // empty | .[]'`) instead of one array read.
- "What runs next" costs one join through `sequenceFlows` instead of a direct field read — mitigated by flows
  living in the same scope object as the nodes that reference them.

## Alternatives Considered

**Stay flat and only add `scopeId` to nodes and flows.** Smallest break and the friendliest shape for `grep` /
`jq` / pasting into an LLM, but nesting stays a reference rather than a structure — which is the substance of
issue #58's complaint — and `children[]` plus `scopeId` reintroduces a smaller version of the redundancy we set
out to remove.

**Keep node→node adjacency alongside `sequenceFlows` for readability.** Best ergonomics for "what comes after
X", and it would feed [#54](https://github.com/Miragon/bpmn-to-code/issues/54) directly, but it keeps the
duplication the issue asks us to remove and still cannot attribute a condition to a branch.

**Emit only `sequenceFlows[]` with no relation fields on nodes.** Fully normalised and smallest, but every
consumer would have to build an index before answering anything — even moddle does not go this far.

**Keep `MESSAGE_START_EVENT` as a convenience field alongside `eventDefinitions[]`.** No consumer would have to
change, but it is derived data that cannot represent a multi-trigger event, so it would be wrong exactly where
it matters.

**Add lanes, data objects, artifacts and collaboration in the same pass.** Deferred: none has a concrete
consumer yet, and the layered shape means they can be added additively within `2.x`.
