package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object CSharpEscalationsWriter : CSharpNameAndCodesWriter() {

    override val type = SharedDefinitionType.ESCALATIONS
    override val doc = "BPMN escalation definitions with name and code, as thrown and caught by the processes."

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<Pair<String, String>>> = definitions.escalations
}
