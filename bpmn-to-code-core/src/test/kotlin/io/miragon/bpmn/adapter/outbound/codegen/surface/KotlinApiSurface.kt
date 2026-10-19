package io.miragon.bpmn.adapter.outbound.codegen.surface

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

/**
 * The public surface of a compiled generated Kotlin type as text: kind, supertypes, properties with their
 * nullable-aware types and values, functions, and nested types. Formatting and imports of the source do not show.
 */
internal class KotlinApiSurface(private val values: SurfaceValues) {

    fun describe(type: KClass<*>): String = buildString { appendType(type, indent = "") }

    private fun StringBuilder.appendType(type: KClass<*>, indent: String) {
        appendLine("$indent${kindOf(type)} ${type.simpleName}${supertypesOf(type)}")
        val instance = type.objectInstance
        val memberIndent = "$indent  "
        publicPropertiesOf(type).forEach { property ->
            appendLine("$memberIndent${describeProperty(property, instance)}")
        }
        type.declaredMemberFunctions.filter { it.visibility == KVisibility.PUBLIC }.map { function ->
            val parameters = function.parameters.drop(1).joinToString { values.shortTypeName(it.type.toString()) }
            "${memberIndent}fun ${function.name}($parameters): ${values.shortTypeName(function.returnType.toString())}"
        }.sorted().forEach { appendLine(it) }
        type.nestedClasses.filter { it.visibility == KVisibility.PUBLIC }.sortedBy { it.simpleName }.forEach { nested ->
            appendType(nested, memberIndent)
        }
    }

    private fun describeProperty(property: KProperty1<out Any, *>, instance: Any?): String {
        val modifier = if (property.isConst) "const val" else "val"
        val declaration = "$modifier ${property.name}: ${values.shortTypeName(property.returnType.toString())}"
        val getter = property.javaGetter
        val constantField = property.javaField?.takeIf { property.isConst }
        return when {
            constantField != null -> "$declaration = ${values.render(constantField.get(null))}"
            instance != null && getter != null -> "$declaration = ${values.invokeAndRender(instance, getter)}"
            else -> declaration
        }
    }

    private fun publicPropertiesOf(type: KClass<*>): List<KProperty1<out Any, *>> = type.memberProperties.filter { it.visibility == KVisibility.PUBLIC }.sortedBy { it.name }

    private fun supertypesOf(type: KClass<*>): String {
        val supertypes = type.supertypes.map { values.shortTypeName(it.toString()) }.filter { it != "Any" }.sorted()
        return if (supertypes.isEmpty()) "" else " : ${supertypes.joinToString()}"
    }

    private fun kindOf(type: KClass<*>): String = when {
        type.objectInstance != null -> "object"
        type.java.isInterface -> "interface"
        else -> "class"
    }
}
