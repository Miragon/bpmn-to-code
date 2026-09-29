package io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaRuntimeTypes
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object JavaMessagesWriter : JavaSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.MESSAGES
    override val javadoc = "BPMN message names used to correlate messages to running process instances.\n"
    override val elementType: ClassName = JavaRuntimeTypes.MESSAGE_NAME

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.messages

    override fun initializer(value: String): CodeBlock = CodeBlock.of($$"new $T($S)", elementType, value)
}
