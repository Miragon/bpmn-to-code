package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.SharedDefinitionsApi

/**
 * Renders the shared definition files of a run in one output language.
 */
internal fun interface SharedDefinitionsBuilder {

    fun buildApiFiles(api: SharedDefinitionsApi): List<GeneratedApiFile>
}
