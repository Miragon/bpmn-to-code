package io.miragon.bpmn.adapter.outbound.codegen

/**
 * The types of the shared definition files, generated once per run next to the Process APIs. `FlowNodes` nodes
 * reference their constants by these names, so the files and the references cannot drift apart.
 *
 * @property runtimeTypeName the `bpmn-to-code-runtime` type a constant of this kind has, e.g. `MessageName`
 */
internal enum class SharedDefinitionType(val typeName: String, val runtimeTypeName: String, val shape: Shape) {
    SERVICE_TASKS("ServiceTasks", "String", Shape.PLAIN_NAME),
    MESSAGES("Messages", "MessageName", Shape.TYPED_NAME),
    SIGNALS("Signals", "SignalName", Shape.TYPED_NAME),
    ERRORS("Errors", "BpmnErrorDefinition", Shape.NAME_AND_CODE),
    ESCALATIONS("Escalations", "BpmnEscalationDefinition", Shape.NAME_AND_CODE),
    PROCESS_VARIABLES("ProcessVariables", "String", Shape.PLAIN_NAME),
    ;

    /**
     * How a constant of a kind is written, e.g. in Kotlin:
     * - [PLAIN_NAME]: `const val CANCEL_CONTRACT_DELEGATE: String = "\${cancelContractDelegate}"`
     * - [TYPED_NAME]: `val MIRAVELO_CONTRACT_SIGNED: MessageName = MessageName("miravelo.contractSigned")`
     * - [NAME_AND_CODE]: `val MIRAVELO_APPLICATION_INVALID: BpmnErrorDefinition = BpmnErrorDefinition(…)`
     */
    enum class Shape { PLAIN_NAME, TYPED_NAME, NAME_AND_CODE }
}
