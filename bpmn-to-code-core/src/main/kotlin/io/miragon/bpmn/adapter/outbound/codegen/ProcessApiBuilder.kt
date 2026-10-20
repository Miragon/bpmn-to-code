package io.miragon.bpmn.adapter.outbound.codegen

/**
 * Renders the Process API of one process in one output language.
 */
internal fun interface ProcessApiBuilder {

    fun render(api: ProcessApiGraph): String
}
