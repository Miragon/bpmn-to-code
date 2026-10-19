package io.miragon.bpmn.adapter.outbound.codegen.surface

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Renders the values and type names of a compiled generated API as stable text. A generated singleton is shown by
 * its name only, so navigation cycles end; any other object is shown with the values of its zero-argument methods.
 */
internal class SurfaceValues(private val generatedPackage: String) {

    fun render(value: Any?): String = when (value) {
        null -> "null"
        is String -> quoted(value)
        is Boolean, is Number -> value.toString()
        is Enum<*> -> "${value.declaringJavaClass.simpleName}.${value.name}"
        is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { render(it) }
        else -> renderObject(value)
    }

    fun invokeAndRender(target: Any?, method: Method): String = try {
        render(method.invoke(target))
    } catch (exception: InvocationTargetException) {
        "<throws ${exception.targetException.javaClass.simpleName}>"
    }

    fun shortTypeName(typeName: String): String = typeName
        .replace("$generatedPackage.", "")
        .replace(RUNTIME_PACKAGE, "")
        .replace("kotlin.collections.", "")
        .replace("kotlin.", "")
        .replace("java.lang.", "")
        .replace("java.util.", "")
        .replace('$', '.')

    fun isSingleton(type: Class<*>): Boolean = type.declaredFields.any { it.name == "INSTANCE" && Modifier.isStatic(it.modifiers) }

    private fun renderObject(value: Any): String {
        val type = value.javaClass
        val name = shortTypeName(type.name)
        if (isGenerated(type) && isSingleton(type)) return "->$name"
        val properties = valueMethods(type).joinToString { method ->
            "${propertyName(method)}=${invokeAndRender(value, method)}"
        }
        return "$name($properties)"
    }

    private fun isGenerated(type: Class<*>) = type.name.startsWith("$generatedPackage.")

    private fun valueMethods(type: Class<*>): List<Method> = type.methods
        .filter { it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) && !it.isBridge && !it.isSynthetic }
        .filter { it.declaringClass != Any::class.java && it.name !in OBJECT_METHODS && !it.name.matches(COMPONENT) }
        .sortedBy { it.name }

    private fun propertyName(method: Method): String {
        val name = method.name
        return if (name.startsWith("get") && name.length > GET_PREFIX_LENGTH) {
            name.substring(GET_PREFIX_LENGTH).replaceFirstChar { it.lowercaseChar() }
        } else {
            name
        }
    }

    private fun quoted(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return "\"$escaped\""
    }

    private companion object {
        const val RUNTIME_PACKAGE = "io.miragon.bpmn.runtime."
        const val GET_PREFIX_LENGTH = 3
        val OBJECT_METHODS = setOf("hashCode", "toString", "getClass")
        val COMPONENT = Regex("component\\d+")
    }
}
