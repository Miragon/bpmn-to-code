package io.miragon.bpmn.adapter.outbound.engine.bpmn

import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnDefinitionsReader.findProcess
import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnDefinitionsReader.normalizeWhitespace
import io.miragon.bpmn.adapter.outbound.engine.dialect.EngineDialect
import io.miragon.bpmn.adapter.outbound.engine.xml.ForeignXmlReader
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventShape
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowScope
import io.miragon.bpmn.domain.shared.GatewayKind
import io.miragon.bpmn.domain.shared.MessageReference
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.SubProcessKind
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.TimerType
import org.camunda.bpm.model.bpmn.instance.Activity
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent
import org.camunda.bpm.model.bpmn.instance.BusinessRuleTask
import org.camunda.bpm.model.bpmn.instance.CallActivity
import org.camunda.bpm.model.bpmn.instance.CatchEvent
import org.camunda.bpm.model.bpmn.instance.CompensateEventDefinition
import org.camunda.bpm.model.bpmn.instance.ComplexGateway
import org.camunda.bpm.model.bpmn.instance.ConditionalEventDefinition
import org.camunda.bpm.model.bpmn.instance.EndEvent
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition
import org.camunda.bpm.model.bpmn.instance.EscalationEventDefinition
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway
import org.camunda.bpm.model.bpmn.instance.EventDefinition
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway
import org.camunda.bpm.model.bpmn.instance.FlowElement
import org.camunda.bpm.model.bpmn.instance.FlowNode
import org.camunda.bpm.model.bpmn.instance.Gateway
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent
import org.camunda.bpm.model.bpmn.instance.IntermediateThrowEvent
import org.camunda.bpm.model.bpmn.instance.LinkEventDefinition
import org.camunda.bpm.model.bpmn.instance.ManualTask
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics
import org.camunda.bpm.model.bpmn.instance.ParallelGateway
import org.camunda.bpm.model.bpmn.instance.ReceiveTask
import org.camunda.bpm.model.bpmn.instance.ScriptTask
import org.camunda.bpm.model.bpmn.instance.SendTask
import org.camunda.bpm.model.bpmn.instance.SequenceFlow
import org.camunda.bpm.model.bpmn.instance.ServiceTask
import org.camunda.bpm.model.bpmn.instance.SignalEventDefinition
import org.camunda.bpm.model.bpmn.instance.StartEvent
import org.camunda.bpm.model.bpmn.instance.SubProcess
import org.camunda.bpm.model.bpmn.instance.Task
import org.camunda.bpm.model.bpmn.instance.TerminateEventDefinition
import org.camunda.bpm.model.bpmn.instance.TimerEventDefinition
import org.camunda.bpm.model.bpmn.instance.Transaction
import org.camunda.bpm.model.bpmn.instance.UserTask
import org.camunda.bpm.model.xml.ModelInstance

/**
 * Reads the engine-independent BPMN structure of a process into the domain's scope tree.
 *
 * A `bpmn:FlowElementsContainer` — the process itself, or any sub-process — owns both its flow nodes and
 * its sequence flows, so the resulting tree mirrors `bpmn:FlowElementsContainer.flowElements` and every
 * flow knows the scope it belongs to. Everything engine-specific is delegated to [EngineDialect].
 */
@Suppress("TooManyFunctions")
internal class BpmnStructureReader(
    private val model: ModelInstance,
    private val dialect: EngineDialect,
) {

    private val extensionReader = ForeignXmlReader(model, dialect.namespace, dialect.fullyReadExtensions)

    private val boundaryEventsByHost: Map<String, List<String>> by lazy {
        model.getModelElementsByType(BoundaryEvent::class.java)
            .mapNotNull { event -> event.attachedTo?.id?.let { host -> host to event.id } }
            .filter { (_, eventId) -> eventId != null }
            .groupBy({ it.first }, { it.second })
    }

    /**
     * Default-flow ids, collected once. BPMN puts `default` on the *source* element, which is also where
     * the domain model keeps it; sequence flows carry the derived flag for the generated `Flows` object.
     */
    private val defaultFlowIds: Set<String> by lazy {
        val fromExclusive = model.getModelElementsByType(ExclusiveGateway::class.java).mapNotNull { it.default?.id }
        val fromInclusive = model.getModelElementsByType(InclusiveGateway::class.java).mapNotNull { it.default?.id }
        val fromActivities = model.getModelElementsByType(Activity::class.java).mapNotNull { it.default?.id }
        (fromExclusive + fromInclusive + fromActivities).toSet()
    }

    /**
     * The root scope of the `bpmn:Process`. Nested scopes are reachable through the sub-process nodes
     * that own them.
     */
    fun read(): FlowScope = readScope(model.findProcess().flowElements)

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
            engineAttributes = extensionReader.foreignAttributesOf(id, dialect.fullyReadAttributesOf(this)),
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
            engineAttributes = extensionReader.foreignAttributesOf(id, dialect.fullyReadAttributesOf(this)),
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
        engineAttributes = extensionReader.foreignAttributesOf(id, dialect.fullyReadAttributesOf(this)),
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
        engineAttributes = extensionReader.foreignAttributesOf(id, dialect.fullyReadAttributesOf(this)),
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
        engineAttributes = extensionReader.foreignAttributesOf(id, dialect.fullyReadAttributesOf(this)),
    )

    private fun FlowNode.toEvent(): FlowNodeDefinition.Event = FlowNodeDefinition.Event(
        id = id,
        shape = eventShape(),
        displayName = displayName(),
        incoming = incomingFlowIds(),
        outgoing = outgoingFlowIds(),
        eventDefinitions = eventDefinitions(),
        attachedToRef = (this as? BoundaryEvent)?.attachedTo?.id,
        interrupting = interrupting(),
        implementation = dialect.implementationOf(this),
        ioMapping = dialect.ioMappingOf(this),
        variables = dialect.variablesOf(this),
        extensions = extensionReader.extensionsOf(id),
        engineAttributes = extensionReader.foreignAttributesOf(id, dialect.fullyReadAttributesOf(this)),
    )

    private fun SequenceFlow.toDefinition(): SequenceFlowDefinition? {
        val sourceRef = source?.id ?: return null
        val targetRef = target?.id ?: return null
        return SequenceFlowDefinition(
            id = id,
            sourceRef = sourceRef,
            targetRef = targetRef,
            flowName = displayName(),
            conditionExpression = conditionExpression?.textContent?.takeIf { it.isNotBlank() },
            isDefault = id != null && id in defaultFlowIds,
        )
    }

    private fun FlowNode.displayName(): String? = name?.normalizeWhitespace()?.takeIf { it.isNotBlank() }

    private fun SequenceFlow.displayName(): String? = name?.normalizeWhitespace()?.takeIf { it.isNotBlank() }

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
        is ReceiveTask -> message?.toReference()
        is SendTask -> message?.toReference()
        else -> null
    }

    private fun org.camunda.bpm.model.bpmn.instance.Message.toReference(): MessageReference = MessageReference(
        messageRef = id ?: name,
        messageName = name,
    )

    private fun FlowNode.eventDefinitions(): List<EventDefinitionInstance> = getChildElementsByType(EventDefinition::class.java).mapNotNull { it.toInstance() }

    @Suppress("CyclomaticComplexMethod")
    private fun EventDefinition.toInstance(): EventDefinitionInstance? = when (this) {
        is TimerEventDefinition -> toTimer()

        is MessageEventDefinition -> EventDefinitionInstance.Message(
            reference = message?.toReference() ?: MessageReference(),
        )

        is SignalEventDefinition -> EventDefinitionInstance.Signal(
            signalRef = signal?.let { it.id ?: it.name },
            signalName = signal?.name,
        )

        is ErrorEventDefinition -> EventDefinitionInstance.Error(
            errorRef = error?.let { it.id ?: it.name },
            errorName = error?.name,
            errorCode = error?.errorCode,
        )

        is EscalationEventDefinition -> EventDefinitionInstance.Escalation(
            escalationRef = escalation?.let { it.id ?: it.name },
            escalationName = escalation?.name,
            escalationCode = escalation?.escalationCode,
        )

        is CompensateEventDefinition -> EventDefinitionInstance.Compensation(
            activityRef = activity?.id,
            waitForCompletion = isWaitForCompletion,
        )

        is ConditionalEventDefinition -> EventDefinitionInstance.Conditional(
            expression = condition?.textContent?.takeIf { it.isNotBlank() },
        )

        is LinkEventDefinition -> EventDefinitionInstance.Link(linkName = name)

        is TerminateEventDefinition -> EventDefinitionInstance.Terminate

        else -> null
    }

    private fun TimerEventDefinition.toTimer(): EventDefinitionInstance.Timer = when {
        timeDate != null -> EventDefinitionInstance.Timer(TimerType.DATE, timeDate.textContent)
        timeDuration != null -> EventDefinitionInstance.Timer(TimerType.DURATION, timeDuration.textContent)
        timeCycle != null -> EventDefinitionInstance.Timer(TimerType.CYCLE, timeCycle.textContent)
        else -> EventDefinitionInstance.Timer()
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
