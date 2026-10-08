package io.miragon.bpmn.domain

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.EventDefinitionInstance
import io.miragon.bpmn.domain.shared.EventDirection
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import io.miragon.bpmn.domain.shared.FlowScope
import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.domain.shared.ProcessGraph
import io.miragon.bpmn.domain.shared.RootElements
import io.miragon.bpmn.domain.shared.SequenceFlowDefinition
import io.miragon.bpmn.domain.shared.ServiceTaskDefinition
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.TaskKind
import io.miragon.bpmn.domain.shared.TimerDefinition
import io.miragon.bpmn.domain.shared.VariableDefinition

/**
 * A process as bpmn-to-code understands it: the content of one BPMN file.
 *
 * [flowNodes] and [sequenceFlows] are the **root scope's** content; a sub-process owns its own children
 * and flows (see [FlowNodeDefinition.Activity.SubProcess]). Consumers that need a flat view use [graph].
 *
 * [definitions] holds the `bpmn:Definitions` root-element registries that nodes reference. Everything
 * else — timers, service-task implementations, call activities and variables — is *derived*
 * from the node tree, so it can never drift from it.
 *
 * [variantName] is what lets several files declare the same process id and still each get an API of their own.
 * See [ADR 004](../../../../../../../docs/contributing/adr/004-bpmn-aligned-domain-model.md).
 */
@Suppress("TooManyFunctions")
data class ProcessModel(
    val processId: String,
    val processName: String? = null,
    val flowNodes: List<FlowNodeDefinition>,
    val sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
    val definitions: RootElements = RootElements(),
    val isExecutable: Boolean = true,
    val detectedEngine: ProcessEngine? = null,
    val variantName: String? = null,
) {

    val graph: ProcessGraph by lazy { ProcessGraph(flowNodes, sequenceFlows) }

    val allFlowNodes: List<FlowNodeDefinition> get() = graph.allFlowNodes

    /**
     * The name this process's generated artifacts carry: its process id, led by [variantName] where it declares one.
     */
    val apiName: String get() = listOfNotNull(variantName, processId).joinToString("_")

    /**
     * One entry per service-task-like node, keyed by the node — two tasks sharing a job type stay two
     * tasks here, so validation can name each of them. The generated API collapses them to one constant.
     */
    val serviceTasks: List<ServiceTaskDefinition>
        get() = allFlowNodes
            .mapNotNull { node -> node.taskImplementation()?.let { ServiceTaskDefinition(node.id, it) } }
            .distinctBy { it.id to it.getRawName() }.sortedBy { it.getRawName() }

    val callActivities: List<CallActivityDefinition>
        get() = allFlowNodes
            .filterIsInstance<FlowNodeDefinition.Activity.CallActivity>()
            .map { it.definition }.sortedBy { it.id.orEmpty() }

    val timers: List<TimerDefinition>
        get() = allFlowNodes
            .filterIsInstance<FlowNodeDefinition.Event>()
            .flatMap { node -> node.eventDefinitions.filterIsInstance<EventDefinitionInstance.Timer>().map { node to it } }
            .map { (node, timer) -> TimerDefinition(id = node.id, type = timer.timerType, expression = timer.expression) }
            .sortedBy { it.id.orEmpty() }

    val variables: List<VariableDefinition>
        get() = allFlowNodes.flatMap { it.variables }.distinct().sortedBy { it.getRawName() }

    /**
     * Every message reference in the process: message events plus send and receive tasks.
     */
    fun messageUsages(): List<NamedEventUsage> {
        val fromEvents = allFlowNodes.filterIsInstance<FlowNodeDefinition.Event>().flatMap { node ->
            node.eventDefinitions
                .filterIsInstance<EventDefinitionInstance.Message>()
                .mapNotNull { it.reference.messageName?.let { name -> NamedEventUsage(node = node, name = name, direction = node.shape.direction) } }
        }
        val fromTasks = allFlowNodes.filterIsInstance<FlowNodeDefinition.Activity.Task>().mapNotNull { node ->
            val name = node.message?.messageName ?: return@mapNotNull null
            val direction = if (node.kind == TaskKind.SEND) EventDirection.THROW else EventDirection.CATCH
            NamedEventUsage(node, name, direction)
        }
        return fromEvents + fromTasks
    }

    /**
     * Every signal reference in the process.
     */
    fun signalUsages(): List<NamedEventUsage> = allFlowNodes
        .filterIsInstance<FlowNodeDefinition.Event>().flatMap { node ->
            node.eventDefinitions
                .filterIsInstance<EventDefinitionInstance.Signal>()
                .mapNotNull { it.signalName?.let { name -> NamedEventUsage(node = node, name = name, direction = node.shape.direction) } }
        }

    /**
     * Every error reference in the process, paired with the event that declares it.
     */
    fun errorUsages(): List<Pair<FlowNodeDefinition, EventDefinitionInstance.Error>> = allFlowNodes
        .filterIsInstance<FlowNodeDefinition.Event>()
        .flatMap { node -> node.eventDefinitions.filterIsInstance<EventDefinitionInstance.Error>().map { node to it } }

    /**
     * Ids of the `bpmn:Definitions` root elements the nodes actually point at.
     *
     * A file may declare more than these — `UnreferencedRootElementRule` reports the difference.
     */
    fun referencedDefinitionIds(): Set<String> = allFlowNodes.flatMapTo(mutableSetOf()) { node ->
        when (node) {
            is FlowNodeDefinition.Event -> node.eventDefinitions.mapNotNull { it.referencedId() }
            is FlowNodeDefinition.Activity.Task -> listOfNotNull(node.message?.messageRef)
            else -> emptyList()
        }
    }

    /**
     * Drops duplicate ids and sorts every scope and registry, so generated output is a function of the model
     * rather than of the order the file happened to be read in.
     */
    fun normalized(): ProcessModel {
        val scope = FlowScope(flowNodes, sequenceFlows).deduplicated().sorted()
        return copy(
            flowNodes = scope.flowNodes,
            sequenceFlows = scope.sequenceFlows,
            definitions = definitions.deduplicated().sorted(),
        )
    }

    private fun EventDefinitionInstance.referencedId(): String? = when (this) {
        is EventDefinitionInstance.Message -> reference.messageRef
        is EventDefinitionInstance.Signal -> signalRef
        is EventDefinitionInstance.Error -> errorRef
        is EventDefinitionInstance.Escalation -> escalationRef
        else -> null
    }

    /**
     * The service-task-like implementation of a node, if the engine dialect resolved one.
     */
    private fun FlowNodeDefinition.taskImplementation(): TaskImplementation? = when (this) {
        is FlowNodeDefinition.Activity.Task -> implementation
        is FlowNodeDefinition.Event -> implementation
        else -> null
    }
}
