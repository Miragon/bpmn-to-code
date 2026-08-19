# 📡 JSON Export

bpmn-to-code generates a structured JSON file alongside the Kotlin/Java API. It contains the full process structure — every flow node, sequence flow, message, signal, error and escalation — in a format that both AI agents and developers can read directly.

Since **6.0.0** the format follows the OMG BPMN 2.0 metamodel and the vocabulary of [`bpmn-moddle`](https://github.com/bpmn-io/bpmn-moddle): element names are the BPMN ones, a scope owns the elements it contains, and relations point at sequence flows. See [ADR 018](https://github.com/Miragon/bpmn-to-code/blob/main/docs/contributing/adr/018-process-json-v2.md) for the rationale, and the [v6 migration guide](/changelog/v6) if you consume the old format.

## What Gets Generated

For each process, a `.json` file is produced, named after the process ID. The format is stable and deterministic — same BPMN in, same JSON out, on every run.

Every file declares the schema it conforms to:

```json
{
    "$schema": "https://miragon.github.io/bpmn-to-code/schema/process-model/2.0.json",
    "formatVersion": "2.0"
}
```

The schema is published as [JSON Schema 2020-12](https://json-schema.org/), so consumers can validate the output and pin a version. It is closed (`additionalProperties: false`) — an unknown field means the file was produced by a newer bpmn-to-code than your schema.

## Document Structure

```
process           the bpmn:Process scope — metadata, flow nodes, sequence flows
definitions       bpmn:Definitions root elements: messages, signals, errors, escalations
variants          per-variant node sets, only for merged multi-variant models
```

## Example

For the newsletter subscription process:

```json
{
    "$schema": "https://miragon.github.io/bpmn-to-code/schema/process-model/2.0.json",
    "formatVersion": "2.0",
    "process": {
        "id": "newsletterSubscription",
        "isExecutable": true,
        "engine": "ZEEBE",
        "flowNodes": [
            {
                "id": "StartEvent_SubmitRegistrationForm",
                "type": "startEvent",
                "name": "Submit newsletter form",
                "outgoing": ["Flow_1csfyyz"],
                "eventDefinitions": [
                    { "type": "message", "messageRef": "Message_FormSubmitted" }
                ],
                "variables": [
                    { "name": "subscriptionId", "direction": "OUTPUT" }
                ]
            },
            {
                "id": "serviceTask_incrementSubscriptionCounter",
                "type": "serviceTask",
                "name": "Increment subscription counter",
                "incoming": ["Flow_1csfyyz"],
                "outgoing": ["Flow_0zdmt0t"],
                "boundaryEventRefs": ["CompensationEvent_OnSubscriptionCounter"],
                "implementation": {
                    "type": "jobWorker",
                    "jobType": "newsletter.incrementCounter"
                }
            },
            {
                "id": "SubProcess_Confirmation",
                "type": "subProcess",
                "name": "Subscription Confirmation",
                "incoming": ["Flow_0zdmt0t"],
                "outgoing": ["Flow_09cuvzp"],
                "boundaryEventRefs": ["ErrorEvent_InvalidMail", "Timer_After3Days"],
                "flowNodes": [
                    {
                        "id": "Activity_SendConfirmationMail",
                        "type": "serviceTask",
                        "name": "Send confirmation mail",
                        "incoming": ["Flow_05i3x1y"],
                        "outgoing": ["Flow_1bckm43"],
                        "implementation": {
                            "type": "jobWorker",
                            "jobType": "newsletter.sendConfirmationMail"
                        }
                    }
                ],
                "sequenceFlows": [
                    {
                        "id": "Flow_05i3x1y",
                        "sourceRef": "StartEvent_RequestReceived",
                        "targetRef": "Activity_SendConfirmationMail"
                    }
                ]
            }
        ],
        "sequenceFlows": [
            {
                "id": "Flow_09cuvzp",
                "sourceRef": "SubProcess_Confirmation",
                "targetRef": "Gateway_SplitNotifications"
            }
        ]
    },
    "definitions": {
        "messages": [
            { "id": "Message_FormSubmitted", "name": "Message_FormSubmitted" }
        ],
        "signals": [
            { "id": "Signal_RegistrationNotPossible", "name": "Signal_RegistrationNotPossible" }
        ],
        "errors": [
            { "id": "Error_InvalidMail", "name": "Error_InvalidMail", "errorCode": "500" }
        ],
        "escalations": []
    }
}
```

## Three layers per element

Every flow node mixes three kinds of information, and knowing which is which tells you how stable a field is:

| Layer | What it is | Stability |
|-------|-----------|-----------|
| **BPMN standard** | `id`, `name`, `type`, containment, `incoming` / `outgoing`, `eventDefinitions`, `attachedToRef`, `cancelActivity`, … | Defined by the OMG spec. Same for every engine. |
| **Normalised facets** | `implementation`, `ioMapping`, `multiInstance`, `variables`, `calledElement` | bpmn-to-code's own shape. Identical across engines; only the expressions inside stay engine-specific. |
| **Raw engine data** | `extensions`, `engineAttributes` | Verbatim, namespaced. Carries what layer 2 does not — an element read in full is left out. |

## Containment and relations

A sub-process owns its children **and its own sequence flows**, mirroring `bpmn:FlowElementsContainer`. A flow always knows which scope it belongs to, and there is no `parentId` to reconstruct nesting from.

`incoming` and `outgoing` hold **sequence-flow IDs**, not node IDs — the same semantics as `bpmn:FlowNode.incoming` / `.outgoing`. To find the next node, resolve the flow:

```js
const nextNodeIds = node.outgoing
    .map(id => scope.sequenceFlows.find(f => f.id === id))
    .map(flow => flow.targetRef)
```

The extra hop is what makes conditions and default flows attributable: the flow object carries `conditionExpression`, and the gateway carries `default`.

## Node Ordering

Flow nodes are sorted in **process-flow order** — a depth-first traversal from the start event(s), per scope. This means the JSON reads top-to-bottom in execution order, without tracing sequence flows manually.

Boundary events appear immediately after the element they are attached to.

## Flow Node Fields

| Field | Always present | Description |
|-------|---------------|-------------|
| `id` | yes | The BPMN element ID |
| `type` | yes | The BPMN element name — `serviceTask`, `userTask`, `startEvent`, `boundaryEvent`, `subProcess`, `callActivity`, `exclusiveGateway`, … Prefix with `bpmn:` to get the `bpmn-moddle` `$type` |
| `name` | no | The element's label from the modeler |
| `incoming` / `outgoing` | no | IDs of the **sequence flows** entering and leaving this node |
| `default` | no | ID of the default sequence flow (on the gateway or activity that owns it) |
| `eventDefinitions` | no | Triggers and results of an event — a list, because BPMN allows several |
| `attachedToRef` | no | Host activity of a boundary event |
| `cancelActivity` | no | Boundary events: whether the event interrupts its host |
| `isInterrupting` | no | Event sub-process start events: whether the event interrupts its scope |
| `triggeredByEvent` | no | Marks a sub-process as an event sub-process |
| `boundaryEventRefs` | no | Boundary events attached to this activity |
| `isForCompensation` | no | Marks an activity as a compensation handler |
| `messageRef` | no | Send and receive tasks reference their message directly |
| `implementation` | no | How the engine runs this node — see below |
| `calledElement` | no | Call activity target plus variable-propagation flags |
| `multiInstance` | no | `bpmn:multiInstanceLoopCharacteristics`, normalised |
| `ioMapping` | no | Input/output parameter mapping, normalised |
| `variables` | no | Variables the node reads or writes, each with a direction |
| `flowNodes` / `sequenceFlows` | no | Children of a sub-process scope |
| `extensions` | no | Foreign-namespace elements, verbatim |
| `engineAttributes` | no | Foreign-namespace attributes, verbatim |

## Event definitions

An event carries a **list** of `eventDefinitions`, discriminated by `type`, because BPMN permits several triggers on one catch event:

```json
"eventDefinitions": [
    { "type": "timer", "timerType": "DURATION", "expression": "PT1M" },
    { "type": "message", "messageRef": "Message_FormSubmitted" }
]
```

| `type` | Payload |
|--------|---------|
| `timer` | `timerType` (`DATE` / `DURATION` / `CYCLE`), `expression` |
| `message` | `messageRef` |
| `signal` | `signalRef` |
| `error` | `errorRef` |
| `escalation` | `escalationRef` |
| `compensation` | `activityRef`, `waitForCompletion` |
| `conditional` | `expression` |
| `link` | `linkName` |
| `terminate` | — |

The `…Ref` fields resolve into `definitions`, where the name and code live. A message used by three events is **one** entry there, referenced three times.

```json
"definitions": {
    "messages": [
        { "id": "Message_FormSubmitted", "name": "Message_FormSubmitted", "correlationKey": "=subscriptionId" }
    ]
}
```

`correlationKey` is the Zeebe `zeebe:subscription` expression. It is declared on the `bpmn:Message` element itself, so it belongs to the message rather than to each referencing event.

## Implementation

`implementation` says how the engine executes a node, normalised across engines and discriminated by `type`:

```json
"implementation": { "type": "jobWorker", "jobType": "newsletter.sendWelcomeMail" }
```

| `type` | Engine | Payload |
|--------|--------|---------|
| `jobWorker` | Zeebe | `jobType`, `retries` |
| `connector` | Zeebe | `jobType`, `templateId`, `retries` |
| `externalTask` | Camunda 7 / Operaton | `topic` |
| `javaClass` | Camunda 7 / Operaton | `className` |
| `delegateExpression` | Camunda 7 / Operaton | `expression` |
| `expression` | Camunda 7 / Operaton | `expression` |

A service task with nothing configured omits the field entirely — that is what the `missing-service-task-implementation` validation rule flags.

## Multi-instance and I/O mappings

Both are activity facets, present only where BPMN allows them.

```json
{
    "id": "serviceTask_sendToSubscriber",
    "type": "serviceTask",
    "multiInstance": {
        "sequential": true,
        "inputCollection": "=subscribers",
        "inputElement": "subscriber"
    }
}
```

`zeebe:loopCharacteristics` and `camunda:collection` / `camunda:elementVariable` both map onto these fields, so the same logical loop reads identically for every engine. The expressions themselves are preserved verbatim — FEEL `=subscribers` for Zeebe, JUEL `${subscribers}` for Camunda 7 — because rewriting them would lose information.

```json
"ioMapping": {
    "inputs": [],
    "outputs": [
        { "target": "subscribers", "source": "=subscribers" },
        { "target": "author", "source": "=author" }
    ]
}
```

`zeebe:ioMapping` and `camunda:inputOutput` both normalise here. `target` is the variable being written, `source` the expression bound to it.

## Engine-specific data

Anything bpmn-to-code does not normalise is preserved verbatim, with its namespace prefix intact:

```json
"extensions": [
    {
        "$type": "zeebe:taskHeaders",
        "children": [
            { "$type": "zeebe:header", "attributes": { "key": "priority", "value": "high" } }
        ]
    }
],
"engineAttributes": { "camunda:asyncBefore": true }
```

`extensions` mirrors `bpmn:extensionElements` and nests arbitrarily; `engineAttributes` holds foreign-namespace attributes on the element itself. A new engine feature shows up here without a schema change.

What is already normalised is **not** repeated here. `zeebe:taskDefinition`, `zeebe:ioMapping`, `zeebe:loopCharacteristics` and `zeebe:calledElement` have typed fields (`implementation`, `ioMapping`, `multiInstance`, `calledElement`), so they are left out rather than stated twice. Camunda 7 and Operaton keep theirs: `camunda:inputParameter` can nest a `camunda:script`, `camunda:in`/`out` carry `businessKey` and `local`, and `camunda:properties` is read for two property names only — for those the raw element is the only complete source.

`engineAttributes` follows the same rule. The attribute behind `implementation` — `camunda:topic`, `camunda:delegateExpression`, `camunda:class` or `camunda:expression`, whichever the engine's precedence picked — is left out; `camunda:asyncBefore`, `camunda:exclusive` and `camunda:type` have no typed counterpart and stay. If a task declares two implementation attributes, only the one that won is dropped, so the other is still reachable.

## Multi-variant processes

When several BPMN files declare the same process ID with different `variantName` values, `process.flowNodes` holds the union and `variants` carries each variant's own node set:

```json
"variants": [
    { "name": "withApproval", "flowNodes": [ /* … */ ], "sequenceFlows": [ /* … */ ] },
    { "name": "express",      "flowNodes": [ /* … */ ], "sequenceFlows": [ /* … */ ] }
]
```

`variants` is absent for single-file processes, so consumers can always read `process` and treat variants as additive.

## Configuring the JSON Task

### Gradle

```kotlin
tasks.named("generateBpmnModelJson", GenerateBpmnJsonTask::class) {
    baseDir = projectDir.toString()
    filePattern = "src/main/resources/**/*.bpmn"
    outputFolderPath = "$projectDir/src/main/resources/bpmn-json"
    processEngine = ProcessEngine.ZEEBE
}
```

Run:

```bash
./gradlew generateBpmnModelJson
```

### Maven

<!-- x-release-please-start-version -->
```xml
<plugin>
    <groupId>io.miragon</groupId>
    <artifactId>bpmn-to-code-maven</artifactId>
    <version>5.2.0</version>
    <executions>
        <execution>
            <id>generate-bpmn-json</id>
            <goals><goal>generate-bpmn-json</goal></goals>
            <configuration>
                <baseDir>${project.basedir}</baseDir>
                <filePattern>src/main/resources/**/*.bpmn</filePattern>
                <outputFolderPath>${project.basedir}/src/main/resources/bpmn-json</outputFolderPath>
                <processEngine>ZEEBE</processEngine>
            </configuration>
        </execution>
    </executions>
</plugin>
```
<!-- x-release-please-end -->

## Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `baseDir` | `String` | required | Base directory for resolving relative paths |
| `filePattern` | `String` | required | Glob pattern to locate BPMN files |
| `outputFolderPath` | `String` | required | Directory where JSON files are written |
| `processEngine` | `ProcessEngine` | required | `ZEEBE`, `CAMUNDA_7`, or `OPERATON` |

## Using the JSON with AI

The JSON is designed for use with AI coding assistants. Paste it into your assistant's context and ask questions about your process:

- "Which service tasks in this process run multi-instance?"
- "What variables does the `Activity_SendConfirmationMail` task receive?"
- "List all boundary events and what they are attached to."

Because the JSON is produced by deterministic rules — not generated by an LLM — the assistant gets reliable process context with no hallucinated element IDs. Aligning the vocabulary with the BPMN standard helps here too: a model that knows BPMN already knows what `boundaryEvent` and `cancelActivity` mean.

::: tip Keep the JSON in version control
Commit the JSON alongside your BPMN files. This makes process structure changes visible in pull request diffs, without requiring reviewers to open Camunda Modeler.
:::
