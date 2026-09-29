package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object CSharpServiceTasksWriter : CSharpConstantsWriter() {

    override val type = SharedDefinitionType.SERVICE_TASKS
    override val doc = "Job worker task types — the task type a C# worker subscribes to."

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.serviceTasks
}
