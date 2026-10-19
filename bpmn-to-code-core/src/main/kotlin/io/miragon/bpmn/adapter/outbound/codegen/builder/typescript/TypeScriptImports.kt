package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.propertyKey

/**
 * Collects what a generated file refers to, so it imports exactly that: the shared definition files, and the runtime
 * types either as values or, when only types are used, type-only.
 */
internal class TypeScriptImports {

    private val sharedTypes = sortedSetOf<String>()
    private var runtimeUsage = RuntimeUsage.NONE

    val sharedDefinitions: Set<String> get() = sharedTypes

    val runtime: RuntimeUsage get() = runtimeUsage

    fun sharedReference(type: SharedDefinitionType, constantName: String): String {
        sharedTypes.add(type.typeName)
        return "${type.typeName}.${propertyKey(constantName)}"
    }

    fun runtimeValue(name: String): String {
        runtimeUsage = RuntimeUsage.VALUE
        return "${TypeScriptRuntimeTypes.ALIAS}.$name"
    }

    fun runtimeType(name: String): String {
        if (runtimeUsage == RuntimeUsage.NONE) {
            runtimeUsage = RuntimeUsage.TYPE
        }
        return "${TypeScriptRuntimeTypes.ALIAS}.$name"
    }

    enum class RuntimeUsage {
        NONE,
        TYPE,
        VALUE,
    }
}
