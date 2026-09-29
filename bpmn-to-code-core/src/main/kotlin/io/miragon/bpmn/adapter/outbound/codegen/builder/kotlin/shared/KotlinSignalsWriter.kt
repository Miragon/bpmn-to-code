package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat.stringLiteral
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object KotlinSignalsWriter : KotlinSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.SIGNALS
    override val kdoc = "BPMN signal names broadcast and caught by signal events."
    override val elementType: TypeName = ClassName(RUNTIME_PACKAGE, "SignalName")

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.signals

    override fun initializer(value: String): CodeBlock = CodeBlock.of("%T(%L)", elementType, stringLiteral(value))
}
