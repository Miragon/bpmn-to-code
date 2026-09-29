package io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object JavaErrorsWriter : JavaSharedDefinitionWriter<Pair<String, String>>() {

    override val type = SharedDefinitionType.ERRORS
    override val javadoc = "BPMN error definitions with name and code, as thrown and caught by the processes.\n"
    override val elementType: ClassName = ClassName.get(RUNTIME_PACKAGE, "BpmnErrorDefinition")

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<Pair<String, String>>> = definitions.errors

    override fun initializer(value: Pair<String, String>): CodeBlock = CodeBlock.of($$"new $T($S, $S)", elementType, value.first, value.second)
}
