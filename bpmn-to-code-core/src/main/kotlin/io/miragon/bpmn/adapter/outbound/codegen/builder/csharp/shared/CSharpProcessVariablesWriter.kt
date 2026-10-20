package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType

internal object CSharpProcessVariablesWriter : CSharpConstantsWriter() {

    override val type = SharedDefinitionType.PROCESS_VARIABLES
    override val doc = "Names of the process variables — the keys a C# worker reads and writes."
}
