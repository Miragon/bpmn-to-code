package io.miragon.bpmn.adapter.outbound.codegen.definitions

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

/**
 * The shared definitions of a run as the files that render them: one group per [SharedDefinitionType] that has
 * entries, in the order of the type. Computed once and rendered per output language, like the `FlowGraph`.
 */
internal data class SharedDefinitionsGraph(val groups: List<Group>) {

    data class Group(val type: SharedDefinitionType, val entries: List<Entry>)

    /**
     * One shared constant. [code] is set for errors and escalations, which carry a name ([value]) and a code.
     */
    data class Entry(val constantName: String, val value: String, val code: String?)

    companion object {

        fun of(definitions: SharedDefinitions): SharedDefinitionsGraph {
            val groups = listOf(
                Group(type = SharedDefinitionType.SERVICE_TASKS, entries = definitions.serviceTasks.map { constantEntry(it) }),
                Group(type = SharedDefinitionType.MESSAGES, entries = definitions.messages.map { constantEntry(it) }),
                Group(type = SharedDefinitionType.SIGNALS, entries = definitions.signals.map { constantEntry(it) }),
                Group(type = SharedDefinitionType.ERRORS, entries = definitions.errors.map { entryWithCode(it) }),
                Group(type = SharedDefinitionType.ESCALATIONS, entries = definitions.escalations.map { entryWithCode(it) }),
                Group(type = SharedDefinitionType.PROCESS_VARIABLES, entries = definitions.processVariables.map { constantEntry(it) }),
            )
            return SharedDefinitionsGraph(groups.filter { it.entries.isNotEmpty() })
        }

        private fun constantEntry(definition: VariableMapping<String>): Entry = Entry(constantName = definition.getName(), value = definition.getValue(), code = null)

        private fun entryWithCode(definition: VariableMapping<Pair<String, String>>): Entry {
            val (name, code) = definition.getValue()
            return Entry(constantName = definition.getName(), value = name, code = code)
        }
    }
}
