package io.miragon.bpmn.adapter.outbound.codegen.surface

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * The public surface of a compiled generated Java type as text: modifiers, supertypes, fields, constructors and
 * methods with the values they return, and its nested types. Formatting and imports of the source do not show.
 */
internal class JavaApiSurface(private val values: SurfaceValues) {

    fun describe(type: Class<*>): String = buildString { appendType(type, indent = "") }

    private fun StringBuilder.appendType(type: Class<*>, indent: String) {
        appendLine("$indent${Modifier.toString(type.modifiers)} ${kindOf(type)} ${type.simpleName}${supertypesOf(type)}")
        val instance = singletonInstanceOf(type)
        val memberIndent = "$indent  "
        type.declaredFields.filter { Modifier.isPublic(it.modifiers) }.sortedBy { it.name }.forEach { field ->
            val owner = if (Modifier.isStatic(field.modifiers)) null else instance
            val value = if (Modifier.isStatic(field.modifiers) || instance != null) " = ${values.render(field.get(owner))}" else ""
            appendLine("$memberIndent${Modifier.toString(field.modifiers)} ${typeOf(field.genericType)} ${field.name}$value")
        }
        type.declaredConstructors.filter { Modifier.isPublic(it.modifiers) }.map { constructor ->
            "${memberIndent}constructor(${constructor.genericParameterTypes.joinToString { typeOf(it) }})"
        }.sorted().forEach { appendLine(it) }
        publicMethodsOf(type).forEach { method -> appendLine("$memberIndent${describeMethod(method, instance)}") }
        inheritedValueMethodsOf(type).takeIf { instance != null }?.forEach { method ->
            appendLine("${memberIndent}inherited ${method.name}() = ${values.invokeAndRender(instance, method)}")
        }
        type.declaredClasses.filter { Modifier.isPublic(it.modifiers) }.sortedBy { it.simpleName }.forEach { nested ->
            appendType(nested, memberIndent)
        }
    }

    private fun describeMethod(method: Method, instance: Any?): String {
        val isStatic = Modifier.isStatic(method.modifiers)
        val parameters = method.genericParameterTypes.joinToString { typeOf(it) }
        val signature = "${Modifier.toString(method.modifiers)} ${typeOf(method.genericReturnType)} ${method.name}($parameters)"
        val canBeCalled = method.parameterCount == 0 && (isStatic || instance != null)
        return if (canBeCalled) "$signature = ${values.invokeAndRender(if (isStatic) null else instance, method)}" else signature
    }

    private fun publicMethodsOf(type: Class<*>): List<Method> = type.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && !it.isBridge && !it.isSynthetic }
        .sortedWith(compareBy({ it.name }, { it.parameterCount }))

    private fun inheritedValueMethodsOf(type: Class<*>): List<Method> = type.methods
        .filter { it.declaringClass != type && it.declaringClass != Any::class.java }
        .filter { it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) && !it.isBridge && !it.isSynthetic }
        .filter { it.name !in setOf("hashCode", "toString") }
        .sortedBy { it.name }

    private fun singletonInstanceOf(type: Class<*>): Any? {
        if (!values.isSingleton(type)) return null
        return type.getDeclaredField("INSTANCE").get(null)
    }

    private fun supertypesOf(type: Class<*>): String {
        val superclass = type.genericSuperclass?.takeIf { it != Any::class.java }?.let { " extends ${typeOf(it)}" }.orEmpty()
        val interfaces = type.genericInterfaces.map { typeOf(it) }.sorted()
        return superclass + if (interfaces.isEmpty()) "" else " implements ${interfaces.joinToString()}"
    }

    private fun kindOf(type: Class<*>) = if (type.isInterface) "interface" else "class"

    private fun typeOf(type: java.lang.reflect.Type) = values.shortTypeName(type.typeName)
}
