# 🔷 Zeebe (Camunda 8)

Engine-specific behavior when using `processEngine = ZEEBE`.

## Service Task Detection

Service tasks are detected via the `zeebe:taskDefinition` extension element:

```xml
<bpmn:serviceTask id="Activity_SendMail" name="Send Mail">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="mail.send" />
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

The `type` attribute becomes the value in the generated `ServiceTasks` object.

## Variable Extraction

### I/O Mappings

Variables are extracted from `zeebe:ioMapping` with `zeebe:input` and `zeebe:output` children:

```xml
<bpmn:extensionElements>
  <zeebe:ioMapping>
    <zeebe:input source="=orderId" target="orderId" />
    <zeebe:output source="=result" target="mailSent" />
  </zeebe:ioMapping>
</bpmn:extensionElements>
```

The `target` attribute of each mapping becomes a variable in the element's `Variables` holder on its `Flow` node.

### Multi-Instance

Multi-instance variables come from `zeebe:loopCharacteristics`:

```xml
<bpmn:multiInstanceLoopCharacteristics>
  <bpmn:extensionElements>
    <zeebe:loopCharacteristics
      inputCollection="=bikeIds"
      inputElement="bikeId"
      outputCollection="orderIds"
      outputElement="=orderId" />
  </bpmn:extensionElements>
</bpmn:multiInstanceLoopCharacteristics>
```

All four attributes are extracted as variables. The `=` expression prefix is automatically stripped.

**Extracted variables:** `bikeIds`, `bikeId`, `orderIds`, `orderId`

## Call Activities

Call activities use `zeebe:calledElement` (not the standard `calledElement` attribute):

```xml
<bpmn:callActivity id="callActivity_cancelBikeOrder" name="Cancel bike order">
  <bpmn:extensionElements>
    <zeebe:calledElement processId="cancelBikeOrder" />
  </bpmn:extensionElements>
</bpmn:callActivity>
```

The `processId` from the extension element becomes `calledProcess` on the call activity's `Flow` node.
