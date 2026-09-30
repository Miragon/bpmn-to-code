package io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object JavaProcessVariablesWriter : JavaSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.PROCESS_VARIABLES
    override val javadoc = "Names of the process variables used in {@code @Variable(name = ProcessVariables.X)} annotations.\n" +
        "Kept as {@code public static final String} because annotation arguments must be compile-time constants.\n"
    override val elementType: ClassName = ClassName.get(String::class.java)

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.processVariables

    override fun initializer(value: String): CodeBlock = CodeBlock.of($$"$S", value)
}
