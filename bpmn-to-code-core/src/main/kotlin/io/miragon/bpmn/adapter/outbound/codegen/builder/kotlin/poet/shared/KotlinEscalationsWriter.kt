package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.shared

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.KotlinCodeFormat
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.KotlinCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

internal object KotlinEscalationsWriter : KotlinSharedDefinitionWriter<Pair<String, String>>() {

    override val type = SharedDefinitionType.ESCALATIONS
    override val kdoc = "BPMN escalation definitions with name and code, as thrown and caught by the processes."
    override val elementType: TypeName = KotlinRuntimeTypes.BPMN_ESCALATION_DEFINITION

    override fun valueOf(entry: SharedDefinitionsGraph.Entry): Pair<String, String> = entry.value to checkNotNull(entry.code)

    override fun initializer(value: Pair<String, String>): CodeBlock = KotlinCodeFormat.namedCall(
        type = elementType,
        "name" to stringLiteral(value.first),
        "code" to stringLiteral(value.second),
        placement = KotlinCodeFormat.Placement.INITIALIZER,
    )
}
