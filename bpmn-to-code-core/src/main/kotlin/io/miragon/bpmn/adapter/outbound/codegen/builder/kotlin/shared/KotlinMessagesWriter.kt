package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinNamesHolder
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object KotlinMessagesWriter : KotlinSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.MESSAGES
    override val kdoc = "BPMN message names used to correlate messages to running process instances."
    override val elementType: TypeName = ClassName(RUNTIME_PACKAGE, "MessageName")

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.messages

    override fun initializer(definition: VariableMapping<String>): CodeBlock = CodeBlock.of("%T(%N.%N)", elementType, KotlinNamesHolder.NAME, definition.getName())

    override fun rawNames(definition: VariableMapping<String>): List<Pair<String, String>> = listOf(definition.getName() to definition.getValue())
}
