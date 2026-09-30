package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object CSharpProcessVariablesWriter : CSharpConstantsWriter() {

    override val type = SharedDefinitionType.PROCESS_VARIABLES
    override val doc = "Names of the process variables — the keys a C# worker reads and writes."

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.processVariables
}
