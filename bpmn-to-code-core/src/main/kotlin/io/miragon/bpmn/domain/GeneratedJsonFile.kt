package io.miragon.bpmn.domain

data class GeneratedJsonFile(val fileName: String, val content: String, val processId: String) {
    companion object {
        fun nameOf(model: ProcessModel): String = "${model.apiName}.json"
    }
}
