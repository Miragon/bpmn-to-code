package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.PackagePath
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ERRORS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.ESCALATIONS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.MESSAGES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.PROCESS_VARIABLES
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SERVICE_TASKS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.SIGNALS
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType.Shape
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionsBuilder
import io.miragon.bpmn.adapter.outbound.codegen.SourceCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSharedDefinitionsModel.Constant
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSharedDefinitionsModel.NameAndCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSharedDefinitionsModel.Names
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSharedDefinitionsModel.NamesAndCodes
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.namespaceOf
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSyntax.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph.Entry
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph.Group
import io.miragon.bpmn.adapter.outbound.codegen.freemarker.TemplateRenderer

/**
 * Generates one C# static class per kind of engine-global identifier, shared by all Process APIs of a run. Names are
 * `const string`s, e.g. `public const string MiraveloContractSigned = "miravelo.contractSigned";`; errors and
 * escalations are a nested class each, holding a `Reference` and a `Code` constant.
 */
internal class CSharpSharedDefinitionsBuilder : SharedDefinitionsBuilder {

    override fun render(group: Group, packagePath: PackagePath): SourceCode {
        val rendered = when (group.type.shape) {
            Shape.PLAIN_NAME, Shape.TYPED_NAME -> TemplateRenderer.render(
                templateName = "csharp/shared-names.ftl",
                model = namesOf(group, packagePath),
            )

            Shape.NAME_AND_CODE -> TemplateRenderer.render(
                templateName = "csharp/shared-names-and-codes.ftl",
                model = namesAndCodesOf(group, packagePath),
            )
        }
        return SourceCode(rendered)
    }

    private fun namesOf(group: Group, packagePath: PackagePath): Names = Names(
        namespace = namespaceOf(packagePath.value),
        typeName = group.type.typeName,
        doc = docOf(group.type),
        constants = group.entries.map { constantOf(group.type, it) },
    )

    private fun namesAndCodesOf(group: Group, packagePath: PackagePath): NamesAndCodes = NamesAndCodes(
        namespace = namespaceOf(packagePath.value),
        typeName = group.type.typeName,
        doc = docOf(group.type),
        definitions = group.entries.map { nameAndCodeOf(group.type, it) },
    )

    private fun constantOf(type: SharedDefinitionType, entry: Entry): Constant = Constant(
        name = CSharpSharedReferences.memberName(type, entry.constantName),
        value = stringLiteral(entry.value),
    )

    private fun nameAndCodeOf(type: SharedDefinitionType, entry: Entry): NameAndCode {
        val className = CSharpSharedReferences.memberName(type, entry.constantName)
        val code = checkNotNull(entry.code) { "${type.typeName} entry ${entry.constantName} has no code" }
        return NameAndCode(
            className = className,
            referenceName = CSharpSharedReferences.referenceMemberName(className),
            reference = stringLiteral(entry.value),
            codeName = CSharpSharedReferences.codeMemberName(className),
            code = stringLiteral(code),
        )
    }

    private fun docOf(type: SharedDefinitionType): String = when (type) {
        SERVICE_TASKS -> "Job worker task types — the task type a C# worker subscribes to."
        MESSAGES -> "BPMN message names used to correlate messages to running process instances."
        SIGNALS -> "BPMN signal names broadcast and caught by signal events."
        ERRORS -> "BPMN error definitions with name and code, as thrown and caught by the processes."
        ESCALATIONS -> "BPMN escalation definitions with name and code, as thrown and caught by the processes."
        PROCESS_VARIABLES -> "Names of the process variables — the keys a C# worker reads and writes."
    }
}
