package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.RootElementDefinition
import io.miragon.bpmn.domain.shared.RootElements
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.TimerType
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection

fun testProcessModel(
    processId: String = "order",
    processName: String? = null,
    variantName: String? = null,
    flowNodes: List<FlowNodeDefinition> = listOf(FlowNodeDefinition.Unknown(id = "create-order")),
    sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
    messages: List<RootElementDefinition.Message> = listOf(RootElementDefinition.Message(id = "messageId", name = "messageName")),
    signals: List<RootElementDefinition.Signal> = listOf(RootElementDefinition.Signal(id = "signalId", name = "signalName")),
    errors: List<RootElementDefinition.Error> = listOf(RootElementDefinition.Error(id = "errorId", name = "errorName", code = "errorCode")),
    escalations: List<RootElementDefinition.Escalation> = emptyList(),
    detectedEngine: ProcessEngine? = null,
    variants: List<ProcessModel.Variant> = emptyList(),
) = ProcessModel(
    processId = processId,
    processName = processName,
    variantName = variantName,
    flowNodes = flowNodes,
    sequenceFlows = sequenceFlows,
    definitions = RootElements(messages, signals, errors, escalations),
    detectedEngine = detectedEngine,
    variants = variants,
)

fun testProcessModelApi(
    model: ProcessModel = testProcessModel(),
    packagePath: String = "packagePath",
    language: OutputLanguage = OutputLanguage.KOTLIN,
    engine: ProcessEngine = ProcessEngine.ZEEBE,
) = BpmnModelApi(model = model, packagePath = packagePath, outputLanguage = language, targetEngine = engine)

/**
 * Polymorphic copy of a [FlowNodeDefinition] with a new [id], across the sealed hierarchy.
 */
fun FlowNodeDefinition.withId(id: String?): FlowNodeDefinition = when (this) {
    is FlowNodeDefinition.Gateway -> copy(id = id)
    is FlowNodeDefinition.Event -> copy(id = id)
    is FlowNodeDefinition.Activity.Task -> copy(id = id)
    is FlowNodeDefinition.Activity.SubProcess -> copy(id = id)
    is FlowNodeDefinition.Activity.CallActivity -> copy(id = id)
    is FlowNodeDefinition.Unknown -> copy(id = id)
}

/**
 * Polymorphic copy of a [FlowNodeDefinition] with a new [displayName], across the sealed hierarchy.
 */
fun FlowNodeDefinition.withDisplayName(displayName: String?): FlowNodeDefinition = when (this) {
    is FlowNodeDefinition.Gateway -> copy(displayName = displayName)
    is FlowNodeDefinition.Event -> copy(displayName = displayName)
    is FlowNodeDefinition.Activity.Task -> copy(displayName = displayName)
    is FlowNodeDefinition.Activity.SubProcess -> copy(displayName = displayName)
    is FlowNodeDefinition.Activity.CallActivity -> copy(displayName = displayName)
    is FlowNodeDefinition.Unknown -> copy(displayName = displayName)
}

/**
 * Convenience builder for a service task backed by a Zeebe job worker.
 */
fun jobWorkerTask(
    id: String,
    jobType: String,
    displayName: String? = null,
    incoming: List<String> = emptyList(),
    outgoing: List<String> = emptyList(),
    variables: List<VariableDefinition> = emptyList(),
    boundaryEventRefs: List<String> = emptyList(),
    engineAttributes: Map<String, Any?> = emptyMap(),
) = FlowNodeDefinition.Activity.Task(
    id = id,
    kind = TaskKind.SERVICE,
    displayName = displayName,
    incoming = incoming,
    outgoing = outgoing,
    implementation = TaskImplementation.JobWorker(jobType),
    boundaryEventRefs = boundaryEventRefs,
    variables = variables,
    engineAttributes = engineAttributes,
)
