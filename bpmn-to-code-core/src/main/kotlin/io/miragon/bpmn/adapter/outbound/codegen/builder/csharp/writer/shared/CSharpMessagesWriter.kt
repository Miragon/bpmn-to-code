package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType

internal object CSharpMessagesWriter : CSharpConstantsWriter() {

    override val type = SharedDefinitionType.MESSAGES
    override val doc = "BPMN message names used to correlate messages to running process instances."
}
