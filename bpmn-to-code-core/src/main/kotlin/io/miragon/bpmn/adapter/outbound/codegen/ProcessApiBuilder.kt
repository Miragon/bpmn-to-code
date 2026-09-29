package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.domain.BpmnModelApi
import io.miragon.bpmn.domain.GeneratedApiFile

/**
 * Renders the Process API of one process in one output language.
 */
internal fun interface ProcessApiBuilder {

    fun buildApiFile(modelApi: BpmnModelApi): GeneratedApiFile
}
