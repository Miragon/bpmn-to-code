package io.miragon.bpmn.domain.shared

import io.miragon.bpmn.domain.utils.StringUtils.toUpperSnakeCase

/**
 * A BPMN flow node, modelled along the OMG class tree: [Gateway], [Event] and the compound [Activity]
 * family ([Activity.Task], [Activity.SubProcess], [Activity.CallActivity]).
 *
 * Every subtype carries exactly the facets BPMN permits on it, so invalid combinations — a multi-instance
 * gateway, a `calledElement` on an event, a `cancelActivity` flag on a task — are unrepresentable. See
 * [ADR 017](../../../../../../../../docs/contributing/adr/017-bpmn-aligned-domain-model.md).
 *
 * [incoming] and [outgoing] hold **sequence-flow ids**, matching `bpmn:FlowNode.incoming` / `.outgoing`.
 * Node-to-node adjacency is derived from the flows themselves — see `ProcessGraph`.
 */
sealed interface FlowNodeDefinition : VariableMapping<String> {

    val id: String?
    val displayName: String?
    val incoming: List<String>
    val outgoing: List<String>
    val variables: List<VariableDefinition>
    val extensions: List<EngineExtension>
    val engineAttributes: Map<String, Any?>

    override fun getName(): String = id?.toUpperSnakeCase() ?: ""
    override fun getValue(): String = id ?: ""
    override fun getRawName(): String = id ?: ""

    /**
     * Unions the additive list fields of [others] into this node, used when merging process variants that
     * declare the same element with variant-specific extension data. Base attributes stay this node's.
     */
    fun mergedWith(others: List<FlowNodeDefinition>): FlowNodeDefinition

    data class Gateway(
        override val id: String?,
        val kind: GatewayKind,
        override val displayName: String? = null,
        override val incoming: List<String> = emptyList(),
        override val outgoing: List<String> = emptyList(),
        val defaultFlow: String? = null,
        override val variables: List<VariableDefinition> = emptyList(),
        override val extensions: List<EngineExtension> = emptyList(),
        override val engineAttributes: Map<String, Any?> = emptyMap(),
    ) : FlowNodeDefinition {
        override fun mergedWith(others: List<FlowNodeDefinition>): FlowNodeDefinition {
            return copy(variables = mergeVariables(this, others))
        }
    }

    /**
     * A BPMN event. [shape] is the structural kind (start / end / intermediate / boundary),
     * [eventDefinitions] the triggers or results it carries — a list, because BPMN allows several.
     *
     * [attachedToRef] and [interrupting] are only populated where BPMN defines them: `attachedToRef` and
     * `cancelActivity` on a boundary event, `isInterrupting` on an event sub-process start event.
     * [implementation] covers `camunda:ServiceTaskLike` on a message throw event.
     */
    data class Event(
        override val id: String?,
        val shape: EventShape,
        override val displayName: String? = null,
        override val incoming: List<String> = emptyList(),
        override val outgoing: List<String> = emptyList(),
        val eventDefinitions: List<EventDefinitionInstance> = emptyList(),
        val attachedToRef: String? = null,
        val interrupting: Boolean? = null,
        val implementation: TaskImplementation? = null,
        val ioMapping: IoMapping? = null,
        override val variables: List<VariableDefinition> = emptyList(),
        override val extensions: List<EngineExtension> = emptyList(),
        override val engineAttributes: Map<String, Any?> = emptyMap(),
    ) : FlowNodeDefinition {
        override fun mergedWith(others: List<FlowNodeDefinition>): FlowNodeDefinition {
            return copy(variables = mergeVariables(this, others))
        }
    }

    /**
     * A BPMN activity. Multi-instance loop characteristics, I/O mappings, boundary-event attachments and
     * the compensation flag are defined on `bpmn:Activity`, so they live here rather than on individual
     * task kinds.
     */
    sealed interface Activity : FlowNodeDefinition {
        val multiInstance: MultiInstanceDefinition?
        val ioMapping: IoMapping?
        val boundaryEventRefs: List<String>
        val isForCompensation: Boolean
        val defaultFlow: String?

        /**
         * An atomic activity. [message] is populated for send and receive tasks, which reference a
         * `bpmn:Message` directly instead of through an event definition.
         */
        data class Task(
            override val id: String?,
            val kind: TaskKind,
            override val displayName: String? = null,
            override val incoming: List<String> = emptyList(),
            override val outgoing: List<String> = emptyList(),
            val implementation: TaskImplementation? = null,
            val message: MessageReference? = null,
            override val multiInstance: MultiInstanceDefinition? = null,
            override val ioMapping: IoMapping? = null,
            override val boundaryEventRefs: List<String> = emptyList(),
            override val isForCompensation: Boolean = false,
            override val defaultFlow: String? = null,
            override val variables: List<VariableDefinition> = emptyList(),
            override val extensions: List<EngineExtension> = emptyList(),
            override val engineAttributes: Map<String, Any?> = emptyMap(),
        ) : Activity {
            override fun mergedWith(others: List<FlowNodeDefinition>): FlowNodeDefinition {
                return copy(
                    variables = mergeVariables(this, others),
                    boundaryEventRefs = mergeBoundaryEventRefs(this, others),
                )
            }
        }

        /**
         * A sub-process, transaction or event sub-process. Owns its children **and its own sequence
         * flows**, mirroring `bpmn:FlowElementsContainer.flowElements`, so a flow always knows its scope.
         */
        data class SubProcess(
            override val id: String?,
            val kind: SubProcessKind,
            override val displayName: String? = null,
            override val incoming: List<String> = emptyList(),
            override val outgoing: List<String> = emptyList(),
            val flowNodes: List<FlowNodeDefinition> = emptyList(),
            val sequenceFlows: List<SequenceFlowDefinition> = emptyList(),
            override val multiInstance: MultiInstanceDefinition? = null,
            override val ioMapping: IoMapping? = null,
            override val boundaryEventRefs: List<String> = emptyList(),
            override val isForCompensation: Boolean = false,
            override val defaultFlow: String? = null,
            override val variables: List<VariableDefinition> = emptyList(),
            override val extensions: List<EngineExtension> = emptyList(),
            override val engineAttributes: Map<String, Any?> = emptyMap(),
        ) : Activity {
            override fun mergedWith(others: List<FlowNodeDefinition>): FlowNodeDefinition {
                return copy(
                    variables = mergeVariables(this, others),
                    boundaryEventRefs = mergeBoundaryEventRefs(this, others),
                )
            }
        }

        data class CallActivity(
            override val id: String?,
            val definition: CallActivityDefinition,
            override val displayName: String? = null,
            override val incoming: List<String> = emptyList(),
            override val outgoing: List<String> = emptyList(),
            override val multiInstance: MultiInstanceDefinition? = null,
            override val ioMapping: IoMapping? = null,
            override val boundaryEventRefs: List<String> = emptyList(),
            override val isForCompensation: Boolean = false,
            override val defaultFlow: String? = null,
            override val variables: List<VariableDefinition> = emptyList(),
            override val extensions: List<EngineExtension> = emptyList(),
            override val engineAttributes: Map<String, Any?> = emptyMap(),
        ) : Activity {
            override fun mergedWith(others: List<FlowNodeDefinition>): FlowNodeDefinition {
                return copy(
                    variables = mergeVariables(this, others),
                    boundaryEventRefs = mergeBoundaryEventRefs(this, others),
                )
            }
        }
    }

    /**
     * Fallback for element types not covered by this model, and the default for manually built nodes.
     */
    data class Unknown(
        override val id: String?,
        override val displayName: String? = null,
        override val incoming: List<String> = emptyList(),
        override val outgoing: List<String> = emptyList(),
        override val variables: List<VariableDefinition> = emptyList(),
        override val extensions: List<EngineExtension> = emptyList(),
        override val engineAttributes: Map<String, Any?> = emptyMap(),
    ) : FlowNodeDefinition {
        override fun mergedWith(others: List<FlowNodeDefinition>): FlowNodeDefinition {
            return copy(variables = mergeVariables(this, others))
        }
    }

    companion object {

        private fun mergeVariables(
            node: FlowNodeDefinition,
            others: List<FlowNodeDefinition>,
        ): List<VariableDefinition> {
            return (node.variables + others.flatMap { it.variables }).distinct()
        }

        private fun mergeBoundaryEventRefs(
            node: Activity,
            others: List<FlowNodeDefinition>,
        ): List<String> {
            val fromOthers = others.filterIsInstance<Activity>().flatMap { it.boundaryEventRefs }
            return (node.boundaryEventRefs + fromOthers).distinct().sorted()
        }
    }
}
