package io.miragon.bpmn.adapter.outbound.json

import io.miragon.bpmn.adapter.outbound.json.model.ProcessModelJson
import io.miragon.bpmn.domain.ProcessModel
import kotlinx.serialization.json.Json

internal class BpmnJsonGenerator(
    private val mapper: BpmnJsonMapper = BpmnJsonMapper(),
) {

    private val json = Json {
        prettyPrint = true
        explicitNulls = false
    }

    fun generate(model: ProcessModel): String {
        val dto = mapper.toJson(model)
        return json.encodeToString(ProcessModelJson.serializer(), dto)
    }
}
