package io.miragon.bpmn.adapter.outbound.codegen.freemarker

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue

/**
 * How a Kotlin or Java flow node refers to a shared constant. The shared types live in the Process API's package, so
 * a reference needs no import.
 */
internal class JvmSharedReferences(private val syntax: JvmSyntax) {

    /**
     * e.g. `Messages.MIRAVELO_CONTRACT_SIGNED`
     */
    fun reference(type: SharedDefinitionType, constantName: String): String {
        val memberName = syntax.identifier(constantName)
        return "${type.typeName}.$memberName"
    }

    /**
     * e.g. `ServiceTasks.MIRAVELO_CANCEL_POLICY`, or `"miravelo.cancelPolicy"` when no shared constant holds the value
     */
    fun constantOrLiteral(type: SharedDefinitionType, sharedValue: SharedValue<String>): String {
        val constant = sharedValue.constant ?: return syntax.stringLiteral(sharedValue.value)
        return reference(type, constant.name)
    }

    fun sharedName(type: SharedDefinitionType, sharedValue: SharedValue<String>): SharedName = SharedName(
        reference = sharedValue.constant?.let { reference(type, it.name) },
        literal = syntax.stringLiteral(sharedValue.value),
    )

    fun sharedNameAndCode(
        type: SharedDefinitionType,
        sharedValue: SharedValue<Pair<String, String>>,
    ): SharedNameAndCode {
        val (name, code) = sharedValue.value
        return SharedNameAndCode(
            reference = sharedValue.constant?.let { reference(type, it.name) },
            name = syntax.stringLiteral(name),
            code = syntax.stringLiteral(code),
        )
    }
}
