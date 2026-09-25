package io.miragon.bpmn.adapter.outbound.codegen.builder

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NamedCode
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedConstant
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.disambiguated
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.writer.CSharpWriter.Companion.toPascalCase

/**
 * Emits a C# `Flow` node's own data: `JobType` (a `const`, so it stays attribute-usable), value properties
 * (`CalledProcess`, `Timer`, `Message`, …), the `AttachedTo` link to a boundary event's host, and the nested
 * holders (`Variables`, `Inputs`, `Outputs`). Job types, messages, signals, errors and escalations refer to
 * their shared definition constant. Only `AttachedTo` crosses into another node, and it is expression-bodied
 * so the node's own initialisation never touches another singleton.
 */
internal class CSharpFacetWriter(private val writer: CSharpWriter) {

    fun writeMembers(facets: NodeFacets) {
        facets.jobType?.let { jobType ->
            jobType.constant
                ?.let { writer.constantExpression("JobType", sharedReference(SharedDefinitionType.SERVICE_TASKS, it)) }
                ?: writer.constant("JobType", jobType.value)
        }
    }

    fun writeProperties(facets: NodeFacets) {
        facets.calledProcessId?.let { writer.readonlyProperty("CalledProcess", runtime("ProcessId"), "new(${stringLiteral(it)})") }
        facets.timer?.let { writer.readonlyProperty("Timer", runtime("BpmnTimer"), "new(${stringLiteral(it.type)}, ${stringLiteral(it.expression)})") }
        facets.message?.let { writer.readonlyProperty("Message", runtime("MessageName"), "new(${nameArgument(SharedDefinitionType.MESSAGES, it)})") }
        facets.signal?.let { writer.readonlyProperty("Signal", runtime("SignalName"), "new(${nameArgument(SharedDefinitionType.SIGNALS, it)})") }
        facets.error?.let { writer.readonlyProperty("Error", runtime("BpmnError"), "new(${namedCodeArguments(SharedDefinitionType.ERRORS, it)})") }
        facets.escalation?.let { writer.readonlyProperty("Escalation", runtime("BpmnEscalation"), "new(${namedCodeArguments(SharedDefinitionType.ESCALATIONS, it)})") }
        facets.attachedTo?.let { writer.expressionProperty("AttachedTo", it.objectName, "${it.objectName}.Instance") }
        facets.isInterrupting?.let { writer.expressionProperty("IsInterrupting", "bool", it.toString()) }
    }

    fun writeHolders(facets: NodeFacets) {
        if (facets.variables.isNotEmpty()) {
            writeVariables(facets.variables)
        }
        if (facets.inputs.isNotEmpty()) {
            writeMappings("Inputs", "InputMappings", facets.inputs)
        }
        if (facets.outputs.isNotEmpty()) {
            writeMappings("Outputs", "OutputMappings", facets.outputs)
        }
    }

    private fun writeVariables(variables: List<VariableFacet>) {
        writer.line()
        writer.readonlyProperty("Variables", "NodeVariables", "new()")
        writer.sealedClass("NodeVariables") {
            variables.forEach { variable ->
                val subtype = runtime("VariableName.${variable.subtype.simpleName}")
                writer.readonlyProperty(variable.rawName.toPascalCase(), subtype, "new(${stringLiteral(variable.rawName)})")
            }
        }
    }

    private fun writeMappings(propertyName: String, holderName: String, mappings: List<MappingFacet>) {
        writer.line()
        writer.readonlyProperty(propertyName, holderName, "new()")
        writer.sealedClass(holderName) {
            mappings.forEach { mapping ->
                writer.readonlyProperty(mapping.target.toPascalCase(), runtime("InputOutputMapping"), mappingInitializer(mapping))
            }
        }
    }

    private fun mappingInitializer(mapping: MappingFacet): String = "new(${stringLiteral(mapping.target)}, ${nullableStringLiteral(mapping.source)}, ${nullableStringLiteral(mapping.sourceExpression)})"

    /**
     * The shared definition files hold plain `const string`s, so a node wraps the referenced constants into its
     * own runtime type; only a value without a shared constant is written as a literal.
     */
    private fun nameArgument(type: SharedDefinitionType, shared: SharedValue<String>): String = shared.constant?.let { sharedReference(type, it) } ?: stringLiteral(shared.value)

    private fun namedCodeArguments(type: SharedDefinitionType, shared: SharedValue<NamedCode>): String = shared.constant
        ?.let { sharedReference(type, it) }
        ?.let { "$it.Reference, $it.Code" }
        ?: "${stringLiteral(shared.value.name)}, ${stringLiteral(shared.value.code)}"

    private fun sharedReference(type: SharedDefinitionType, constant: SharedConstant): String = "${type.typeName}.${disambiguated(constant.rawName.toPascalCase(), type.typeName)}"

    private fun runtime(typeName: String): String = "${CSharpRuntimeTypes.CLASS_NAME}.$typeName"
}
