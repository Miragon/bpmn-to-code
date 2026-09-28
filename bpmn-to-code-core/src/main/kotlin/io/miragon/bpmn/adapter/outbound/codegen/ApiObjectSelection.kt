package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.domain.BpmnModelApi

/**
 * Decides which sections a generated Process API contains.
 *
 * Today the only question is whether a section would have anything to say about the model — a process
 * whose models are merged gets `FlowVariants` rather than `Flow`. That is a mapping from the
 * codegen vocabulary onto the domain, so it lives here, once, rather than in each language's builder or
 * on [ApiObjectType] itself.
 *
 * Letting the caller choose the sections is a second, independent question. It is not implemented, but
 * this is where it goes: [selectFrom] gains the requested set and nothing else has to move.
 */
internal object ApiObjectSelection {

    /**
     * Whether [type] has anything to contribute for [modelApi].
     */
    fun includes(type: ApiObjectType, modelApi: BpmnModelApi): Boolean = type.hasContentIn(modelApi)

    private fun ApiObjectType.hasContentIn(modelApi: BpmnModelApi): Boolean {
        val model = modelApi.model
        return when (this) {
            ApiObjectType.PROCESS_ID, ApiObjectType.PROCESS_ENGINE -> true
            ApiObjectType.FLOW -> !model.isMerged && model.allFlowNodes.isNotEmpty()
            ApiObjectType.FLOW_VARIANTS -> model.isMerged
        }
    }
}
