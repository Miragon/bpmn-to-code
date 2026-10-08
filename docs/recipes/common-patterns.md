# 📖 Common Patterns

Practical patterns and best practices for working with bpmn-to-code.

## Naming Conventions

Your element IDs directly shape the generated API. Use consistent prefixes for clean, readable constants:

| Element Type | Pattern | Example |
|-------------|---------|---------|
| Tasks | `Activity_` | `Activity_SendEmail`, `Activity_ProcessPayment` |
| Start Events | `StartEvent_` | `StartEvent_FormSubmitted` |
| End Events | `EndEvent_` | `EndEvent_OrderCompleted` |
| Timers | `Timer_` | `Timer_After3Days`, `Timer_EveryMorning` |
| Messages | `Message_` | `Message_OrderReceived` |
| Errors | `Error_` | `Error_InvalidData` |
| Signals | `Signal_` | `Signal_CancellationRequested` |

**Avoid:** generic IDs like `Task_1` or `Event_abc123`.

## Explicit Variable Definitions

bpmn-to-code only extracts variables from **explicit variable definitions** in the BPMN model — not from expressions in sequence flows, gateways, or script tasks. The supported sources depend on the engine:

| Source | Camunda 7 / Operaton | Zeebe |
|--------|----------------------|-------|
| I/O mappings | ✅ | ✅ |
| Multi-instance attributes | ✅ | ✅ |
| Call activity in/out mappings | ✅ | — |
| `additionalInputVariables` / `additionalOutputVariables` extension properties | ✅ | — |

**Do:** Define variables explicitly in I/O mappings.

::: code-group

```xml [Camunda 7]
<camunda:inputOutput>
  <camunda:inputParameter name="subscriptionId">${subscriptionId}</camunda:inputParameter>
  <camunda:outputParameter name="mailSent">true</camunda:outputParameter>
</camunda:inputOutput>
```

```xml [Zeebe]
<zeebe:ioMapping>
  <zeebe:input source="=subscriptionId" target="subscriptionId" />
  <zeebe:output source="=mailSent" target="mailSent" />
</zeebe:ioMapping>
```

:::

**Don't rely on** variables only referenced in expressions:

```xml
<!-- This variable won't appear in the generated API -->
<bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">
  ${execution.getVariable('decision') == 'ACCEPTED'}
</bpmn:conditionExpression>
```

## Additional Input / Output Variables (Camunda 7 / Operaton)

Some elements don't support I/O mappings — for example, **message start events** in Camunda 7 and Operaton. Variables arriving with the triggering message won't be captured by `camunda:inputOutput` / `operaton:inputOutput`, so they would be missing from the generated API.

The workaround is to declare them explicitly using two directional extension properties — `additionalInputVariables` and `additionalOutputVariables`. Values become `VariableName.Input` / `VariableName.Output` entries in the element's `Variables` holder on its `FlowNodes` node:

::: code-group

```xml [Camunda 7]
<bpmn:extensionElements>
  <camunda:properties>
    <camunda:property name="additionalInputVariables" value="orderId, customerEmail" />
    <camunda:property name="additionalOutputVariables" value="processingResult" />
  </camunda:properties>
</bpmn:extensionElements>
```

```xml [Operaton]
<bpmn:extensionElements>
  <operaton:properties>
    <operaton:property name="additionalInputVariables" value="orderId, customerEmail" />
    <operaton:property name="additionalOutputVariables" value="processingResult" />
  </operaton:properties>
</bpmn:extensionElements>
```

:::

Each comma-separated value becomes a variable in the generated API under the corresponding direction. Works on any BPMN element, not just start events. The legacy undirected `additionalVariables` property is no longer extracted.

::: tip
See the engine pages for full details: [Camunda 7](/engines/camunda7#additional-input-output-variables-extension-properties) · [Operaton](/engines/operaton#additional-input-output-variables-extension-properties)
:::

## One Process, Several Models

The same process sometimes exists in several models — per location, per customer group, per environment — that
share one `processId`. Every BPMN file gets a Process API of its own, so the files need names that tell them
apart: give all but one a `variantName` extension property on the process.

```
default/order-process.bpmn     -> processId="orderProcess"
                               -> OrderProcessApi

corporate/order-process.bpmn   -> processId="orderProcess", variantName="corporate"
                               -> CorporateOrderProcessApi
```

**Guidelines:**
- Leave the `variantName` off the model the others deviate from, so it keeps the plain name
- Each API holds exactly what its file declares; nothing is merged between the files
- Both APIs carry the same `PROCESS_ID`, since the engine knows one process

::: warning
Without a `variantName`, generation fails as soon as two files declare the same `processId` and names both files.
See [Several files, one process id](/guide/generated-api#several-files-one-process-id) for the XML.
:::

