package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiModel.NameAndCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.disambiguated
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue

/**
 * How a shared constant is named in its C# class, and how a flow node refers to it. Both use the same names, so a
 * renamed member is referenced as such.
 */
internal object CSharpSharedReferences {

    /**
     * e.g. `MIRAVELO_CONTRACT_SIGNED` in `Messages` is `MiraveloContractSigned`
     */
    fun memberName(type: SharedDefinitionType, constantName: String): String {
        val pascalCaseName = pascalCase(constantName)
        return disambiguated(pascalCaseName, type.typeName)
    }

    /**
     * The member holding the name of an error or escalation class, e.g. `Reference` in `MiraveloApplicationInvalid`
     */
    fun referenceMemberName(className: String): String = disambiguated("Reference", className)

    /**
     * The member holding the code of an error or escalation class, e.g. `Code` in `MiraveloApplicationInvalid`
     */
    fun codeMemberName(className: String): String = disambiguated("Code", className)

    /**
     * e.g. `ProcessVariables.ApplicationId`
     */
    fun reference(type: SharedDefinitionType, constantName: String): String {
        val memberName = memberName(type, constantName)
        return "${type.typeName}.$memberName"
    }

    /**
     * e.g. `Messages.MiraveloContractSigned`, or `"miravelo.contractSigned"` when no shared constant holds the value
     */
    fun constantOrLiteral(type: SharedDefinitionType, sharedValue: SharedValue<String>): String {
        val constant = sharedValue.constant ?: return stringLiteral(sharedValue.value)
        return reference(type, constant.name)
    }

    /**
     * e.g. `Errors.MiraveloApplicationInvalid.Reference` and `Errors.MiraveloApplicationInvalid.Code`, or
     * `"miravelo.applicationInvalid"` and `"applicationInvalid"` when no shared constant holds them
     */
    fun nameAndCode(type: SharedDefinitionType, sharedValue: SharedValue<Pair<String, String>>): NameAndCode {
        val constant = sharedValue.constant
        if (constant == null) {
            val (name, code) = sharedValue.value
            return NameAndCode(name = stringLiteral(name), code = stringLiteral(code))
        }
        val className = memberName(type, constant.name)
        val classReference = "${type.typeName}.$className"
        return NameAndCode(
            name = "$classReference.${referenceMemberName(className)}",
            code = "$classReference.${codeMemberName(className)}",
        )
    }
}
