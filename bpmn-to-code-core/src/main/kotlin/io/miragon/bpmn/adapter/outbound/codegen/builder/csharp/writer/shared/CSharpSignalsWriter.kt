package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType

internal object CSharpSignalsWriter : CSharpConstantsWriter() {

    override val type = SharedDefinitionType.SIGNALS
    override val doc = "BPMN signal names broadcast and caught by signal events."
}
