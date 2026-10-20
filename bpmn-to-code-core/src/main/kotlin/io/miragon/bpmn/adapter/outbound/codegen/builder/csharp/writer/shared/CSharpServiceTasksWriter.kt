package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType

internal object CSharpServiceTasksWriter : CSharpConstantsWriter() {

    override val type = SharedDefinitionType.SERVICE_TASKS
    override val doc = "Job worker task types — the task type a C# worker subscribes to."
}
