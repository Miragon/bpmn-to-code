package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

internal object KotlinSignalsWriter : KotlinSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.SIGNALS
    override val kdoc = "BPMN signal names broadcast and caught by signal events."
    override val elementType: TypeName = KotlinRuntimeTypes.SIGNAL_NAME

    override fun valueOf(entry: SharedDefinitionsGraph.Entry): String = entry.value

    override fun initializer(value: String): CodeBlock = CodeBlock.of("%T(%L)", elementType, stringLiteral(value))
}
