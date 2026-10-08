# 🔧 Engines

`processEngine` selects which engine namespace bpmn-to-code reads. The BPMN structure itself — elements, sequence flows, event definitions, messages, signals, errors, escalations, timers — is read the same way for every engine.

| Value | Engine | Namespace read |
|-------|--------|----------------|
| `ZEEBE` | Camunda 8 / Zeebe | `zeebe:` (`http://camunda.org/schema/zeebe/1.0`) |
| `CAMUNDA_7` | Camunda 7 | `camunda:` (`http://camunda.org/schema/1.0/bpmn`) |
| `OPERATON` | Operaton | `operaton:` (`http://operaton.org/schema/1.0/bpmn`) |

Camunda 7 and Operaton are read by the same code and differ only in the namespace. Every `camunda:` name below is `operaton:` for Operaton.

## What is extracted

| | Zeebe | Camunda 7 / Operaton |
|---|---|---|
| **Job type** → [`ServiceTasks`](/guide/generated-api#shared-definitions), `jobType` | `type` of `zeebe:taskDefinition`, on any task or event | On a service task, or on the message event definition of an event: the first of `camunda:topic`, `camunda:delegateExpression`, `camunda:class`, `camunda:expression` |
| **I/O mapping** → [variables](/guide/generated-api#variables-with-direction) | `zeebe:input` / `zeebe:output` in `zeebe:ioMapping`: the `target` becomes an input / output variable | `camunda:inputParameter` / `camunda:outputParameter` in `camunda:inputOutput`: the `name` becomes an input / output variable |
| **Multi-instance** → variables | `zeebe:loopCharacteristics`: `inputCollection` and `inputElement` are inputs, `outputCollection` and `outputElement` outputs | `camunda:collection` and `camunda:elementVariable` on `multiInstanceLoopCharacteristics`: both are inputs |
| **Called process** → `calledProcess` | `processId` of `zeebe:calledElement` | `calledElement` attribute of the call activity |
| **Call-activity mappings** → [`Inputs` / `Outputs`](/guide/generated-api#call-activity-variable-mappings) | `zeebe:input` / `zeebe:output` of the call activity's `zeebe:ioMapping` | `camunda:in` / `camunda:out` with `source`, `sourceExpression` and `target` |
| **Call-activity variables** | the mapping `target`s, as for any I/O mapping | `source` of `camunda:in` (input), a `sourceExpression` that is a plain variable reference (input), `target` of `camunda:out` (output) |
| **`additionalInputVariables` / `additionalOutputVariables`** | not read | `camunda:property` in `camunda:properties`, on any flow node |
| **`variantName`** on the process | `zeebe:property` | `camunda:property` |
| **[JSON export](/surface/json) only** | `retries` of the task definition, the connector template (`zeebe:modelerTemplate`), the `correlationKey` of a message's `zeebe:subscription`, `propagateAllParentVariables` / `propagateAllChildVariables` | `variables="all"` on `camunda:in` / `camunda:out` |

Multi-instance values lose their expression syntax: `=bikeIds` and `${bikeIds}` both become the variable `bikeIds`. A variable that is only used inside an expression — a gateway condition, a script — is not extracted; see [Modeling](/guide/modeling#variables).

Extension elements and engine attributes that are not listed here are not lost: the JSON export carries them [unparsed](/surface/json#engine-specific-data).

## Zeebe

- Any task or event with a `zeebe:taskDefinition` gets a job type, a send task or a message end event as much as a service task. A connector is such a task: its job type is the connector's (`io.camunda:http-json:1`), and its `zeebe:input` targets appear as variables of the node.
- A call activity names its process in `zeebe:calledElement`. The standard `calledElement` attribute is not read.
- The mapping `source` is kept as written (`=orderIds`), in the call activity's `Inputs` / `Outputs` and in the JSON.

```xml
<bpmn:serviceTask id="serviceTask_sendContract" name="Send contract">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="miravelo.sendContract" />
    <zeebe:ioMapping>
      <zeebe:input source="=applicationId" target="applicationId" />
      <zeebe:output source="=contractId" target="contractId" />
    </zeebe:ioMapping>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

## Camunda 7 and Operaton

- When a service task carries several implementation attributes, the first in the order topic, delegate expression, class, expression wins.
- Only service tasks and message event definitions yield a job type. A send task or business rule task with a `camunda:topic` does not.
- Message start events cannot carry `camunda:inputOutput`. Declare what they provide with [`additionalInputVariables` / `additionalOutputVariables`](/guide/modeling#variables).
- On a call activity, a `sourceExpression` adds a variable only when it is a plain reference such as `${applicationId}`. Literals (`${true}`, `${null}`) and anything more complex (`${order.id}`) stay in `Inputs` only.
- An Operaton model that still uses the `camunda:` namespace is a Camunda 7 model to bpmn-to-code: generate it with `CAMUNDA_7`.

```xml
<bpmn:serviceTask id="serviceTask_orderBike" name="Order bike"
    camunda:type="external" camunda:topic="miravelo.orderBike">
  <bpmn:multiInstanceLoopCharacteristics
      camunda:collection="${bikeIds}" camunda:elementVariable="bikeId" />
</bpmn:serviceTask>
```

## CIB seven

There is no engine value for CIB seven. Its models use the `camunda:` namespace, so they are read with `CAMUNDA_7`. bpmn-to-code has no CIB seven fixtures and does not test it separately.

## Engine check

bpmn-to-code detects the engine a file was modelled for from its namespaces. Generating with a different `processEngine` fails with the [`engine-mismatch`](/validate/) rule; a file without any engine namespace produces a warning.
