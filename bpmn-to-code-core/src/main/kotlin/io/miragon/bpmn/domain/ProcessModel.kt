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
 * A process as bpmn-to-code understands it — whether it came from one BPMN file or from several that
 * declare the same process id.
 *
 * [flowNodes] and [sequenceFlows] are the **root scope's** content; a sub-process owns its own children
 * and flows (see [FlowNodeDefinition.Activity.SubProcess]). Consumers that need a flat view use [graph].
 *
 * [definitions] holds the `bpmn:Definitions` root-element registries that nodes reference. Everything
 * else — timers, service-task implementations, call activities and variables — is *derived*
 * from the node tree, so it can never drift from it.
 *
 * [variants] is empty for a single-file process and holds the per-variant node sets once several files
 * have been merged; [flowNodes] then carries their union, so a consumer that ignores variants still sees
 * a complete process. See [ADR 017](../../../../../../../docs/contributing/adr/017-bpmn-aligned-domain-model.md).
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
    val variants: List<Variant> = emptyList(),
) {

    val graph: ProcessGraph by lazy { ProcessGraph(flowNodes, sequenceFlows) }

    val allFlowNodes: List<FlowNodeDefinition> get() = graph.allFlowNodes

    /**
     * True once several BPMN files declaring this process id have been merged into one model.
     */
    val isMerged: Boolean get() = variants.isNotEmpty()

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

    private fun EventDefinitionInstance.referencedId(): String? = when (this) {
        is EventDefinitionInstance.Message -> reference.messageRef
        is EventDefinitionInstance.Signal -> signalRef
        is EventDefinitionInstance.Error -> errorRef
        is EventDefinitionInstance.Escalation -> escalationRef
        else -> null
    }

    /**
     * A single file still goes through the merge, so duplicate ids inside one model collapse the same way.
     */
    private fun deduplicated(): ProcessModel {
        val merged = scope().merge(emptyList())
        return copy(flowNodes = merged.flowNodes, sequenceFlows = merged.sequenceFlows, definitions = definitions.merge(emptyList()))
    }

    /**
     * Sorts every scope and registry, so generated output is a function of the model rather than of the order
     * the files happened to be read in.
     */
    private fun sorted(): ProcessModel {
        val sorted = scope().sorted()
        return copy(
            flowNodes = sorted.flowNodes,
            sequenceFlows = sorted.sequenceFlows,
            definitions = definitions.sorted(),
            variants = variants.map { variant ->
                val sortedVariant = FlowScope(variant.flowNodes, variant.sequenceFlows).sorted()
                variant.copy(flowNodes = sortedVariant.flowNodes, sequenceFlows = sortedVariant.sequenceFlows)
            },
        )
    }

    /**
     * Merging and sorting treat a scope as one value, so it is read out here. The models themselves name
     * their two halves rather than storing the pair.
     */
    private fun scope() = FlowScope(flowNodes, sequenceFlows)

    /**
     * The service-task-like implementation of a node, if the engine dialect resolved one.
     */
    private fun FlowNodeDefinition.taskImplementation(): TaskImplementation? = when (this) {
        is FlowNodeDefinition.Activity.Task -> implementation
        is FlowNodeDefinition.Event -> implementation
        else -> null
    }

    /**
     * One merged-in BPMN file: the same process id, modelled differently.
     */
    data class Variant(
        val variantName: String,
        val flowNodes: List<FlowNodeDefinition> = emptyList(),
        val sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
    ) {
        val graph: ProcessGraph by lazy { ProcessGraph(flowNodes, sequenceFlows) }
    }

    companion object {

        /**
         * Merges process models by process id, each merged model sorted.
         * A process backed by a single BPMN file keeps an empty [variants].
         * A process backed by several files gains one [Variant] per file, with [flowNodes] holding their union.
         */
        fun mergeByProcessId(models: List<ProcessModel>): List<ProcessModel> = models.groupBy { it.processId }.entries
            .sortedBy { it.key }.map { (processId, modelsOfProcess) -> merge(processId, modelsOfProcess).sorted() }

        /**
         * A merged node's base attributes come from the first model. The models are sorted by `variantName` first,
         * so the result is a deterministic function of the inputs rather than of filesystem read order.
         */
        private fun merge(processId: String, models: List<ProcessModel>): ProcessModel {
            if (models.size == 1) return models.first().deduplicated()
            require(models.none { it.variantName.isNullOrBlank() }) {
                "Multiple BPMN files share process ID '$processId' but not all define a variantName. " +
                    "Add a variantName extension property to each process."
            }
            val sorted = models.sortedBy { requireNotNull(it.variantName) }
            val merged = sorted.first().scope().merge(sorted.drop(1).map { it.scope() })
            return ProcessModel(
                processId = processId,
                processName = sorted.firstNotNullOfOrNull { it.processName },
                flowNodes = merged.flowNodes,
                sequenceFlows = merged.sequenceFlows,
                definitions = sorted.first().definitions.merge(sorted.drop(1).map { it.definitions }),
                isExecutable = sorted.any { it.isExecutable },
                detectedEngine = sorted.firstNotNullOfOrNull { it.detectedEngine },
                variants = sorted.map { Variant(variantName = requireNotNull(it.variantName), flowNodes = it.flowNodes, sequenceFlows = it.sequenceFlows) },
            )
        }
    }
}
