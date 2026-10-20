package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.shared

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.KotlinCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

internal object KotlinProcessVariablesWriter : KotlinSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.PROCESS_VARIABLES
    override val kdoc = "Names of the process variables used in `@Variable(name = ProcessVariables.X)` annotations.\n" +
        "Kept as `const val String` because annotation arguments must be compile-time constants."
    override val elementType: TypeName = String::class.asTypeName()
    override val modifiers = listOf(KModifier.CONST)

    override fun valueOf(entry: SharedDefinitionsGraph.Entry): String = entry.value

    override fun initializer(value: String): CodeBlock = stringLiteral(value)
}
