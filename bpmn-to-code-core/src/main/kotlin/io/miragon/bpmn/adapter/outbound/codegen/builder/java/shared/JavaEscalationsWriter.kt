package io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaRuntimeTypes
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object JavaEscalationsWriter : JavaSharedDefinitionWriter<Pair<String, String>>() {

    override val type = SharedDefinitionType.ESCALATIONS
    override val javadoc = "BPMN escalation definitions with name and code, as thrown and caught by the processes.\n"
    override val elementType: ClassName = JavaRuntimeTypes.BPMN_ESCALATION_DEFINITION

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<Pair<String, String>>> = definitions.escalations

    override fun initializer(value: Pair<String, String>): CodeBlock = CodeBlock.of($$"new $T($S, $S)", elementType, value.first, value.second)
}
