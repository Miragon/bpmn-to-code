package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object CSharpMessagesWriter : CSharpConstantsWriter() {

    override val type = SharedDefinitionType.MESSAGES
    override val doc = "BPMN message names used to correlate messages to running process instances."

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.messages
}
