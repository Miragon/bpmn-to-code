package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object CSharpSignalsWriter : CSharpConstantsWriter() {

    override val type = SharedDefinitionType.SIGNALS
    override val doc = "BPMN signal names broadcast and caught by signal events."

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.signals
}
