package io.miragon.bpmn.runtime

/**
 * The sequence flows from one node to the same [target]. Usually there is exactly one, reachable as [flow]; when
 * several flows lead to the same element, [flows] keeps all of them so one can be picked by condition, name or id.
 */
data class SequenceFlows<out TARGET : FlowNode>(
    override val target: TARGET,
    val flows: List<SequenceFlow<TARGET>>,
) : Successor<TARGET> {

    init {
        require(flows.isNotEmpty()) { "at least one sequence flow must lead to $target" }
    }

    constructor(target: TARGET, vararg flows: SequenceFlow<TARGET>) : this(target, flows.toList())

    val flow: SequenceFlow<TARGET>
        get() = flows.singleOrNull() ?: error("${flows.size} sequence flows lead to $target; pick one of `flows`")

    companion object {
        @JvmStatic
        @JvmOverloads
        fun <TARGET : FlowNode> single(
            flowId: ElementId,
            name: String? = null,
            conditionExpression: String? = null,
            isDefault: Boolean = false,
            target: TARGET,
        ): SequenceFlows<TARGET> = SequenceFlows(
            target,
            SequenceFlow(id = flowId, name = name, conditionExpression = conditionExpression, isDefault = isDefault, target = target),
        )
    }
}
