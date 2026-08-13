package io.miragon.bpmn.domain.shared

/**
 * The `bpmn:Definitions` root-element registries of a process, keyed by each element's own id.
 *
 * These four lists are always handled together — merged, filtered and sorted as a unit — so they travel as
 * one value rather than as four parallel fields on every model, service and mapper. Flow nodes point into
 * them through their `…Ref` fields; see [RootElementDefinition].
 */
data class RootElements(
    val messages: List<RootElementDefinition.Message> = emptyList(),
    val signals: List<RootElementDefinition.Signal> = emptyList(),
    val errors: List<RootElementDefinition.Error> = emptyList(),
    val escalations: List<RootElementDefinition.Escalation> = emptyList(),
) {

    /**
     * Unions [others] into this registry, keyed by the element's **own id** (falling back to its name for
     * the rare id-less element) — the same key the extractor uses and the one flow nodes reference.
     * Deduplicating by name alone would drop one of two same-named `bpmn:Message` elements and leave its
     * `messageRef` dangling.
     */
    fun merge(others: List<RootElements>): RootElements {
        val all = listOf(this) + others
        return RootElements(
            messages = all.flatMap { it.messages }.distinctById(),
            signals = all.flatMap { it.signals }.distinctById(),
            errors = all.flatMap { it.errors }.distinctById(),
            escalations = all.flatMap { it.escalations }.distinctById(),
        )
    }

    /**
     * Sorted by name, so generated output is a function of the model rather than of read order.
     */
    fun sorted(): RootElements {
        return RootElements(
            messages = messages.sortedBy { it.getRawName() },
            signals = signals.sortedBy { it.getRawName() },
            errors = errors.sortedBy { it.getRawName() },
            escalations = escalations.sortedBy { it.getRawName() },
        )
    }

    private fun <T> List<T>.distinctById(): List<T> where T : VariableMapping<*>, T : RootElementDefinition {
        return filter { it.getRawName().isNotEmpty() }.distinctBy { it.id ?: it.getRawName() }
    }
}
