package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

internal object KotlinErrorsWriter : KotlinSharedDefinitionWriter<Pair<String, String>>() {

    override val type = SharedDefinitionType.ERRORS
    override val kdoc = "BPMN error definitions with name and code, as thrown and caught by the processes."
    override val elementType: TypeName = KotlinRuntimeTypes.BPMN_ERROR_DEFINITION

    override fun valueOf(entry: SharedDefinitionsGraph.Entry): Pair<String, String> = entry.value to checkNotNull(entry.code)

    override fun initializer(value: Pair<String, String>): CodeBlock = KotlinCodeFormat.namedCall(
        type = elementType,
        "name" to stringLiteral(value.first),
        "code" to stringLiteral(value.second),
        placement = KotlinCodeFormat.Placement.INITIALIZER,
    )
}
