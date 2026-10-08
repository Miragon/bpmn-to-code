# ✏️ Modeling for bpmn-to-code

The generated API is only as readable as the model it comes from: element ids become class names, message and job type names become constants, and only variables the model declares exist in code. This page collects what to pay attention to in the modeler. The example models in [`shared/bpmn/`](https://github.com/Miragon/bpmn-to-code/tree/main/shared/bpmn) follow it.

## Naming

Give every element a `type_camelCase` id: the prefix mirrors the BPMN element type, the rest says what the element does. The id becomes the name of the node, so `serviceTask_sendContract` is `FlowNodes.ServiceTaskSendContract`.

| Element | Example id |
|---|---|
| Start event | `startEvent_leasingRequestReceived` |
| Intermediate event | `event_contractSigned` |
| Boundary event | `boundary_applicationInvalid`, `timer_signatureReminder` |
| End event | `endEvent_leasingActive` |
| Service task | `serviceTask_orderBike` |
| User task | `userTask_updateDeliveryAddress` |
| Send / receive task | `sendTask_sendConfirmationMail`, `receiveTask_handoverReported` |
| Business rule task | `businessRuleTask_checkCreditRating` |
| Gateway | `gateway_isSolvent` |
| Subprocess | `subProcess_concludeContract` |
| Call activity | `callActivity_cancelBikeOrder` |
| Sequence flow | `flow_isSolventToConcludeContract` |
| Message, signal, error, escalation | `message_contractSigned`, `signal_memberActivated`, `error_applicationInvalid`, `escalation_contractNotSigned` |

- **Process ids** are camelCase and name the business capability: `bikeLeasing`, `cancelBikeOrder`. The API is named after it: `BikeLeasingProcessApi`.
- **Message, signal, error and escalation names** follow `<domain>.<state>`: `miravelo.contractSigned` becomes `Messages.MIRAVELO_CONTRACT_SIGNED`. The constant is derived from the name, not from the id.
- **Job types and external-task topics** follow `<domain>.<what the task does>`: `miravelo.orderBike` becomes `ServiceTasks.MIRAVELO_ORDER_BIKE`.
- **Label every flow leaving a gateway** (`Yes` / `No`). The label is the `name` of the generated sequence flow.

Avoid the ids a modeler generates (`Activity_0x7f3a`, `Flow_1abc`): they produce names nobody can read, and they change when an element is redrawn. Two ids that lead to the same name, or one that shadows a name of the API itself, fail generation; see the [validation rules](/validate/).

## Variables {#variables}

bpmn-to-code extracts the variables the model **declares**: I/O mappings, multi-instance collections and element variables, and call-activity mappings. The [engine matrix](/engines/#what-is-extracted) lists the exact elements and the direction each one gets.

A variable that only occurs inside an expression is not extracted. This is deliberate: the model is the contract for its variables.

```xml
<!-- `solvent` does not become a variable through this condition -->
<bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=not(solvent)</bpmn:conditionExpression>
```

So declare what a task reads and writes on the task:

::: code-group

```xml [Zeebe]
<zeebe:ioMapping>
  <zeebe:input source="=applicationId" target="applicationId" />
  <zeebe:output source="=contractId" target="contractId" />
</zeebe:ioMapping>
```

```xml [Camunda 7]
<camunda:inputOutput>
  <camunda:inputParameter name="applicationId">${applicationId}</camunda:inputParameter>
  <camunda:outputParameter name="contractId">${contractId}</camunda:outputParameter>
</camunda:inputOutput>
```

```xml [Operaton]
<operaton:inputOutput>
  <operaton:inputParameter name="applicationId">${applicationId}</operaton:inputParameter>
  <operaton:outputParameter name="contractId">${contractId}</operaton:outputParameter>
</operaton:inputOutput>
```

:::

### Where no mapping is possible (Camunda 7, Operaton)

Some elements cannot carry an I/O mapping, a message start event for one. For these, list the variables in the extension properties `additionalInputVariables` and `additionalOutputVariables`. They work on any flow node:

::: code-group

```xml [Camunda 7]
<bpmn:startEvent id="startEvent_leasingRequestReceived" name="Leasing request received">
  <bpmn:extensionElements>
    <camunda:properties>
      <camunda:property name="additionalInputVariables" value="applicationId, bikeIds, monthlyNetIncome, age" />
    </camunda:properties>
  </bpmn:extensionElements>
</bpmn:startEvent>
```

```xml [Operaton]
<bpmn:startEvent id="startEvent_leasingRequestReceived" name="Leasing request received">
  <bpmn:extensionElements>
    <operaton:properties>
      <operaton:property name="additionalInputVariables" value="applicationId, bikeIds, monthlyNetIncome, age" />
    </operaton:properties>
  </bpmn:extensionElements>
</bpmn:startEvent>
```

:::

Each comma-separated name becomes a `VariableName.Input` on the node; `additionalOutputVariables` does the same for `VariableName.Output`. Zeebe does not read these properties: there, a start event takes a `zeebe:ioMapping` like any other element.

## Several files, one process id {#several-files-one-process-id}

Every BPMN file gets a Process API of its own, named after its process id. Two files declaring the same process id would be generated under one name, so generation fails and names both files.

To keep such files side by side — the same process modelled per location or per customer group — give them a `variantName`, an extension property on the process. It leads the name of what is generated from that file; the process id stays untouched.

::: code-group

```xml [Zeebe]
<bpmn:process id="bikeLeasing" isExecutable="true">
  <bpmn:extensionElements>
    <zeebe:properties>
      <zeebe:property name="variantName" value="corporate" />
    </zeebe:properties>
  </bpmn:extensionElements>
  <!-- ... -->
</bpmn:process>
```

```xml [Camunda 7]
<bpmn:process id="bikeLeasing" isExecutable="true">
  <bpmn:extensionElements>
    <camunda:properties>
      <camunda:property name="variantName" value="corporate" />
    </camunda:properties>
  </bpmn:extensionElements>
  <!-- ... -->
</bpmn:process>
```

```xml [Operaton]
<bpmn:process id="bikeLeasing" isExecutable="true">
  <bpmn:extensionElements>
    <operaton:properties>
      <operaton:property name="variantName" value="corporate" />
    </operaton:properties>
  </bpmn:extensionElements>
  <!-- ... -->
</bpmn:process>
```

:::

| File | `variantName` | Generated API | JSON file | `PROCESS_ID` |
|------|---------------|---------------|-----------|--------------|
| `default/bike-leasing.bpmn` | — | `BikeLeasingProcessApi` | `bikeLeasing.json` | `bikeLeasing` |
| `corporate/bike-leasing.bpmn` | `corporate` | `CorporateBikeLeasingProcessApi` | `corporate_bikeLeasing.json` | `bikeLeasing` |

- A file without a `variantName` keeps the plain name. Leave it off the model the others deviate from.
- A file with a `variantName` always carries it in its name, whether or not another file shares its process id. Adding a second file later never renames an existing API.
- The name starts with a letter and may contain letters, digits, `_` and `-`. As in a process id, `_` and `-` separate words: `corporate-fleet` leads to `CorporateFleetBikeLeasingProcessApi`.
- Nothing is merged between the files. Each API holds what its own file declares; their job types, messages and variables meet in the [shared definitions](/guide/generated-api#shared-definitions), as for any two processes.
- Name the variant after what sets the model apart (`corporate`), not after a version. Models that are different processes should get different process ids instead.

## Non-executable processes

A process with `isExecutable="false"` gets no Process API: generation skips it. The [JSON export](/surface/json) still writes its file, and the [validate task](/validate/) still checks it. A process without the attribute is treated as executable.

## One process per file

bpmn-to-code reads the first `bpmn:process` of a file. In a collaboration diagram with several pools, the other processes are ignored. Keep each process you want an API for in a file of its own.
