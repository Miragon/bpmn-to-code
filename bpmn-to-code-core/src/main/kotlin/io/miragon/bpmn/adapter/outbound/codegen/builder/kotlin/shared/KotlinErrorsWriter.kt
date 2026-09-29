package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.shared

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import io.miragon.bpmn.adapter.outbound.codegen.NameAndCodeConstants
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinCodeFormat
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.KotlinNamesHolder
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object KotlinErrorsWriter : KotlinSharedDefinitionWriter<Pair<String, String>>() {

    override val type = SharedDefinitionType.ERRORS
    override val kdoc = "BPMN error definitions with name and code, as thrown and caught by the processes."
    override val elementType: TypeName = ClassName(RUNTIME_PACKAGE, "BpmnErrorDefinition")

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<Pair<String, String>>> = definitions.errors

    override fun initializer(definition: VariableMapping<Pair<String, String>>): CodeBlock {
        val constants = NameAndCodeConstants(definition)
        return KotlinCodeFormat.namedCall(
            type = elementType,
            "name" to CodeBlock.of("%N.%N", KotlinNamesHolder.NAME, constants.nameConstant),
            "code" to CodeBlock.of("%N.%N", KotlinNamesHolder.NAME, constants.codeConstant),
            placement = KotlinCodeFormat.Placement.INITIALIZER,
        )
    }

    override fun rawNames(definition: VariableMapping<Pair<String, String>>): List<Pair<String, String>> = NameAndCodeConstants(definition).rawValues()
}
