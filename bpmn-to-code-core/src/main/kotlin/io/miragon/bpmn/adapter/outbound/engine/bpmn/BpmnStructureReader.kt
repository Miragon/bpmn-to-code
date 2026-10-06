package io.miragon.bpmn.adapter.outbound.engine.bpmn

import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnDefinitionsReader.findProcess
import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnDefinitionsReader.normalizeWhitespace
import io.miragon.bpmn.adapter.outbound.engine.dialect.EngineDialect
import io.miragon.bpmn.adapter.outbound.engine.xml.ForeignXmlReader
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowScope
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind
import org.camunda.bpm.model.bpmn.instance.Activity
import org.camunda.bpm.model.bpmn.instance.Artifact
import org.camunda.bpm.model.bpmn.instance.Association
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent
import org.camunda.bpm.model.bpmn.instance.BusinessRuleTask
import org.camunda.bpm.model.bpmn.instance.CallActivity
import org.camunda.bpm.model.bpmn.instance.CatchEvent
import org.camunda.bpm.model.bpmn.instance.CompensateEventDefinition
import org.camunda.bpm.model.bpmn.instance.ComplexGateway
import org.camunda.bpm.model.bpmn.instance.EndEvent
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway
import org.camunda.bpm.model.bpmn.instance.FlowElement
import org.camunda.bpm.model.bpmn.instance.FlowNode
import org.camunda.bpm.model.bpmn.instance.Gateway
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent
import org.camunda.bpm.model.bpmn.instance.IntermediateThrowEvent
import org.camunda.bpm.model.bpmn.instance.ManualTask
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics
import org.camunda.bpm.model.bpmn.instance.ParallelGateway
import org.camunda.bpm.model.bpmn.instance.ReceiveTask
import org.camunda.bpm.model.bpmn.instance.ScriptTask
import org.camunda.bpm.model.bpmn.instance.SendTask
import org.camunda.bpm.model.bpmn.instance.SequenceFlow
import org.camunda.bpm.model.bpmn.instance.ServiceTask
import org.camunda.bpm.model.bpmn.instance.StartEvent
import org.camunda.bpm.model.bpmn.instance.SubProcess
import org.camunda.bpm.model.bpmn.instance.Task
import org.camunda.bpm.model.bpmn.instance.Transaction
import org.camunda.bpm.model.bpmn.instance.UserTask
import org.camunda.bpm.model.xml.ModelInstance
import org.camunda.bpm.model.xml.instance.ModelElementInstance

/**
 * Reads the engine-independent BPMN structure of a process into the domain's scope tree.
 *
 * A `bpmn:FlowElementsContainer` — the process itself, or any sub-process — owns both its flow nodes and
 * its sequence flows, so the resulting tree mirrors `bpmn:FlowElementsContainer.flowElements` and every
 * flow knows the scope it belongs to. Everything engine-specific is delegated to [EngineDialect].
 */
@Suppress("TooManyFunctions")
internal class BpmnStructureReader(private val model: ModelInstance, private val dialect: EngineDialect) {

    private val extensionReader = ForeignXmlReader(modelInstance = model, engineNamespace = dialect.namespace, fullyReadExtensions = dialect.fullyReadExtensions)

    private val process = model.findProcess()

    private val boundaryEventsByHost: Map<String, List<String>> by lazy {
        boundaryEventsIn(process.flowElements)
            .mapNotNull { event -> event.attachedTo?.id?.let { host -> host to event.id } }
            .filter { (_, eventId) -> eventId != null }.groupBy({ it.first }, { it.second })
    }

    private val compensationHandlerByEvent: Map<String, String> by lazy {
        associationsIn(artifacts = process.artifacts, elements = process.flowElements)
            .mapNotNull { it.toCompensationHandlerOfEvent() }
            .distinctBy { (eventId, _) -> eventId }.toMap()
    }

    /**
     * The root scope of the `bpmn:Process`. Nested scopes are reachable through the sub-process nodes
     * that own them.
     */
    fun read(): FlowScope = readScope(process.flowElements)

    private fun boundaryEventsIn(elements: Collection<FlowElement>): List<BoundaryEvent> = elements.flatMap { element ->
        when (element) {
            is BoundaryEvent -> listOf(element)
            is SubProcess -> boundaryEventsIn(element.flowElements)
            else -> emptyList()
        }
    }

    private fun associationsIn(artifacts: Collection<Artifact>, elements: Collection<FlowElement>): List<Association> {
        val nested = elements.filterIsInstance<SubProcess>()
            .flatMap { associationsIn(artifacts = it.artifacts, elements = it.flowElements) }
        return artifacts.filterIsInstance<Association>() + nested
    }

    /**
     * A compensation boundary event points at its handler through an association; associations to anything else —
     * a text annotation, an activity that is no compensation handler, an element of another process — are no handlers.
     */
    private fun Association.toCompensationHandlerOfEvent(): Pair<String, String>? {
        val eventId = (source as? BoundaryEvent)?.takeIf { it.isCompensationEvent() }?.id
        val handlerId = (target as? Activity)?.takeIf { it.isForCompensation && it.isPartOfProcess() }?.id
        return if (eventId != null && handlerId != null) eventId to handlerId else null
    }

    private fun BoundaryEvent.isCompensationEvent(): Boolean = eventDefinitions.any { it is CompensateEventDefinition }

    private fun ModelElementInstance.isPartOfProcess(): Boolean = generateSequence(parentElement) { it.parentElement }.any { it == process }

    private fun readScope(elements: Collection<FlowElement>): FlowScope = FlowScope(
        flowNodes = elements.filterIsInstance<FlowNode>().map { it.toDefinition() },
        sequenceFlows = elements.filterIsInstance<SequenceFlow>().mapNotNull { it.toDefinition() },
    )

    private fun FlowNode.toDefinition(): FlowNodeDefinition = when (this) {
        is SubProcess -> toSubProcess()

        is CallActivity -> toCallActivity()

        is Gateway -> toGateway()

        is CatchEvent, is org.camunda.bpm.model.bpmn.instance.ThrowEvent -> toEvent()

        is Task -> toTask()

        else -> FlowNodeDefinition.Unknown(
            id = id,
            displayName = displayName(),
            incoming = incomingFlowIds(),
            outgoing = outgoingFlowIds(),
            variables = dialect.variablesOf(this),
            extensions = extensionReader.extensionsOf(id),
            engineAttributes = engineAttributes(),
        )
    }

    private fun SubProcess.toSubProcess(): FlowNodeDefinition.Activity.SubProcess {
        val (children, childFlows) = readScope(flowElements)
        return FlowNodeDefinition.Activity.SubProcess(
            id = id,
            kind = subProcessKind(),
            displayName = displayName(),
            incoming = incomingFlowIds(),
            outgoing = outgoingFlowIds(),
            flowNodes = children,
            sequenceFlows = childFlows,
            multiInstance = multiInstance(),
            ioMapping = dialect.ioMappingOf(this),
            boundaryEventRefs = boundaryEventRefs(),
            isForCompensation = isForCompensation,
            defaultFlow = defaultFlowId(),
            variables = dialect.variablesOf(this),
            extensions = extensionReader.extensionsOf(id),
            engineAttributes = engineAttributes(),
        )
    }

    private fun CallActivity.toCallActivity(): FlowNodeDefinition.Activity.CallActivity = FlowNodeDefinition.Activity.CallActivity(
        id = id,
        definition = dialect.callActivityOf(this),
        displayName = displayName(),
        incoming = incomingFlowIds(),
        outgoing = outgoingFlowIds(),
        multiInstance = multiInstance(),
        ioMapping = dialect.ioMappingOf(this),
        boundaryEventRefs = boundaryEventRefs(),
        isForCompensation = isForCompensation,
        defaultFlow = defaultFlowId(),
        variables = dialect.variablesOf(this),
        extensions = extensionReader.extensionsOf(id),
        engineAttributes = engineAttributes(),
    )

    private fun Task.toTask(): FlowNodeDefinition.Activity.Task = FlowNodeDefinition.Activity.Task(
        id = id,
        kind = taskKind(),
        displayName = displayName(),
        incoming = incomingFlowIds(),
        outgoing = outgoingFlowIds(),
        implementation = dialect.implementationOf(this),
        message = taskMessage(),
        multiInstance = multiInstance(),
        ioMapping = dialect.ioMappingOf(this),
        boundaryEventRefs = boundaryEventRefs(),
        isForCompensation = isForCompensation,
        defaultFlow = defaultFlowId(),
        variables = dialect.variablesOf(this),
        extensions = extensionReader.extensionsOf(id),
        engineAttributes = engineAttributes(),
    )

    private fun Gateway.toGateway(): FlowNodeDefinition.Gateway = FlowNodeDefinition.Gateway(
        id = id,
        kind = gatewayKind(),
        displayName = displayName(),
        incoming = incomingFlowIds(),
        outgoing = outgoingFlowIds(),
        defaultFlow = defaultFlowId(),
        variables = dialect.variablesOf(this),
        extensions = extensionReader.extensionsOf(id),
        engineAttributes = engineAttributes(),
    )

    private fun FlowNode.toEvent(): FlowNodeDefinition.Event = FlowNodeDefinition.Event(
        id = id,
        shape = eventShape(),
        displayName = displayName(),
        incoming = incomingFlowIds(),
        outgoing = outgoingFlowIds(),
        eventDefinitions = EventDefinitionReader.eventDefinitionsOf(this),
        attachedToRef = (this as? BoundaryEvent)?.attachedTo?.id,
        interrupting = interrupting(),
        compensationHandlerRef = id?.let { compensationHandlerByEvent[it] },
        implementation = dialect.implementationOf(this),
        ioMapping = dialect.ioMappingOf(this),
        variables = dialect.variablesOf(this),
        extensions = extensionReader.extensionsOf(id),
        engineAttributes = engineAttributes(),
    )

    /**
     * BPMN puts `default` on the *source* element, which is also where the domain model keeps it; sequence
     * flows carry the derived flag for the generated `SequenceFlows`.
     */
    private fun SequenceFlow.toDefinition(): SequenceFlowDefinition? {
        val sourceNode = source ?: return null
        val sourceRef = sourceNode.id ?: return null
        val targetRef = target?.id ?: return null
        return SequenceFlowDefinition(
            id = id,
            sourceRef = sourceRef,
            targetRef = targetRef,
            flowName = displayName(),
            conditionExpression = conditionExpression?.textContent?.takeIf { it.isNotBlank() },
            isDefault = id != null && sourceNode.defaultFlowId() == id,
        )
    }

    private fun FlowElement.displayName(): String? = name?.normalizeWhitespace()?.takeIf { it.isNotBlank() }

    private fun FlowNode.engineAttributes(): Map<String, Any?> {
        val readByDialect = dialect.fullyReadAttributesOf(this)
        return extensionReader.foreignAttributesOf(id, fullyRead = readByDialect)
    }

    private fun FlowNode.incomingFlowIds(): List<String> = incoming.mapNotNull { it.id }

    private fun FlowNode.outgoingFlowIds(): List<String> = outgoing.mapNotNull { it.id }

    private fun FlowNode.boundaryEventRefs(): List<String> = id?.let { boundaryEventsByHost[it] }.orEmpty()

    private fun FlowNode.defaultFlowId(): String? = when (this) {
        is ExclusiveGateway -> default?.id
        is InclusiveGateway -> default?.id
        is Activity -> default?.id
        else -> null
    }

    private fun Activity.multiInstance(): MultiInstanceDefinition? {
        val loop = loopCharacteristics as? MultiInstanceLoopCharacteristics ?: return null
        val base = MultiInstanceDefinition(
            sequential = loop.isSequential,
            cardinality = loop.loopCardinality?.textContent?.takeIf { it.isNotBlank() },
            completionCondition = loop.completionCondition?.textContent?.takeIf { it.isNotBlank() },
        )
        return dialect.multiInstanceBindingsOf(loop, base)
    }

    private fun Task.taskMessage(): MessageReference? = when (this) {
        is ReceiveTask -> message?.let { EventDefinitionReader.referenceOf(it) }
        is SendTask -> message?.let { EventDefinitionReader.referenceOf(it) }
        else -> null
    }

    /**
     * Whether the event interrupts its enclosing scope, defaulting to `true` per the BPMN spec when the
     * attribute is absent. Meaningful only for boundary events (`cancelActivity`) and event sub-process
     * start events (`isInterrupting`); `null` for every other event.
     */
    private fun FlowNode.interrupting(): Boolean? = when {
        this is BoundaryEvent -> cancelActivity()
        this is StartEvent && (parentElement as? SubProcess)?.triggeredByEvent() == true -> isInterrupting
        else -> null
    }

    private fun FlowNode.eventShape(): EventShape = when (this) {
        is BoundaryEvent -> EventShape.BOUNDARY_EVENT
        is StartEvent -> EventShape.START_EVENT
        is EndEvent -> EventShape.END_EVENT
        is IntermediateCatchEvent -> EventShape.INTERMEDIATE_CATCH_EVENT
        is IntermediateThrowEvent -> EventShape.INTERMEDIATE_THROW_EVENT
        else -> EventShape.INTERMEDIATE_CATCH_EVENT
    }

    private fun SubProcess.subProcessKind(): SubProcessKind = when {
        this is Transaction -> SubProcessKind.TRANSACTION
        triggeredByEvent() -> SubProcessKind.EVENT
        else -> SubProcessKind.PLAIN
    }

    private fun Task.taskKind(): TaskKind = when (this) {
        is ServiceTask -> TaskKind.SERVICE
        is UserTask -> TaskKind.USER
        is ReceiveTask -> TaskKind.RECEIVE
        is SendTask -> TaskKind.SEND
        is ScriptTask -> TaskKind.SCRIPT
        is ManualTask -> TaskKind.MANUAL
        is BusinessRuleTask -> TaskKind.BUSINESS_RULE
        else -> TaskKind.NONE
    }

    private fun Gateway.gatewayKind(): GatewayKind = when (this) {
        is ExclusiveGateway -> GatewayKind.EXCLUSIVE
        is ParallelGateway -> GatewayKind.PARALLEL
        is InclusiveGateway -> GatewayKind.INCLUSIVE
        is EventBasedGateway -> GatewayKind.EVENT_BASED
        is ComplexGateway -> GatewayKind.COMPLEX
        else -> GatewayKind.EXCLUSIVE
    }
}
