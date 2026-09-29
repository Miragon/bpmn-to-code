package io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaNamesHolder
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object JavaSignalsWriter : JavaSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.SIGNALS
    override val javadoc = "BPMN signal names broadcast and caught by signal events.\n"
    override val elementType: ClassName = ClassName.get(RUNTIME_PACKAGE, "SignalName")

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.signals

    override fun initializer(definition: VariableMapping<String>): CodeBlock = CodeBlock.of($$"new $T($N.$N)", elementType, JavaNamesHolder.NAME, definition.getName())

    override fun rawNames(definition: VariableMapping<String>): List<Pair<String, String>> = listOf(definition.getName() to definition.getValue())
}
