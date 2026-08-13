package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * Registry entries reduced to the constants the Process API can actually declare.
 *
 * The domain keeps every `bpmn:Definitions` root element, because flow nodes reference them by id and two
 * of them may legitimately share a name. The generated API has no such id — a name yields exactly one
 * constant — so the collapsing happens here, at the point where names become identifiers.
 */
internal fun <T : VariableMapping<*>> List<T>.asApiConstants(): List<T> {
    return filter { it.getRawName().isNotEmpty() }.distinctBy { it.getRawName() }
}
