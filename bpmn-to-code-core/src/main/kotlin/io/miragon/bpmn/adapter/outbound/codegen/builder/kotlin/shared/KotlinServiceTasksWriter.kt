package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat.stringLiteral
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object KotlinServiceTasksWriter : KotlinSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.SERVICE_TASKS
    override val kdoc = "Job worker task types used in `@JobWorker(type = ServiceTasks.X)` annotations.\n" +
        "Kept as `const val String` because annotation arguments must be compile-time constants."
    override val elementType: TypeName = String::class.asTypeName()
    override val modifiers = listOf(KModifier.CONST)

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<String>> = definitions.serviceTasks

    override fun initializer(value: String): CodeBlock = stringLiteral(value)
}
