package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType

internal object CSharpErrorsWriter : CSharpNameAndCodesWriter() {

    override val type = SharedDefinitionType.ERRORS
    override val doc = "BPMN error definitions with name and code, as thrown and caught by the processes."
}
