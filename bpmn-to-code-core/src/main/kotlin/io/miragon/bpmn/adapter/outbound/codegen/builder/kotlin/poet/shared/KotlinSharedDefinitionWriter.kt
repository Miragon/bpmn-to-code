package io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.shared

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.poet.KotlinCodeFormat
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue

/**
 * Writes the Kotlin object of one kind of shared definition: a property per definition plus `all`.
 */
internal abstract class KotlinSharedDefinitionWriter<T : Any> {

    abstract val type: SharedDefinitionType
    protected abstract val kdoc: String
    protected abstract val elementType: TypeName
    protected open val modifiers: List<KModifier> = emptyList()

    protected abstract fun valueOf(entry: SharedDefinitionsGraph.Entry): T

    protected abstract fun initializer(value: T): CodeBlock

    fun write(entries: List<SharedDefinitionsGraph.Entry>): TypeSpec {
        val properties = entries.map { property(name = it.constantName, initializer = initializer(valueOf(it))) }
        return TypeSpec.objectBuilder(type.typeName).addKdoc(kdoc)
            .addProperties(properties)
            .addProperty(allDefinitions(entries))
            .build()
    }

    /**
     * A node's property implementing its runtime facet interface with a value of this kind: its shared constant — the
     * shared types live in the Process API's package, so the plain name resolves without an import — or the value
     * itself when no shared constant holds it.
     */
    fun nodeProperty(name: String, shared: SharedValue<T>): PropertySpec {
        val constant = shared.constant
        val initializer = if (constant != null) reference(constant) else initializer(shared.value)
        return PropertySpec.builder(name, elementType).addModifiers(KModifier.OVERRIDE).initializer(initializer).build()
    }

    fun reference(constant: SharedConstant): CodeBlock = CodeBlock.of("%L.%N", type.typeName, constant.name)

    private fun property(name: String, initializer: CodeBlock): PropertySpec = PropertySpec.builder(name, elementType)
        .addModifiers(modifiers).initializer(initializer).build()

    private fun allDefinitions(entries: List<SharedDefinitionsGraph.Entry>): PropertySpec = PropertySpec.builder("all", LIST.parameterizedBy(elementType))
        .initializer(KotlinCodeFormat.listOfNames(entries.map { it.constantName })).build()
}
