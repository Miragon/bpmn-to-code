package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.pascalCase
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpErrorsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpEscalationsWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpMessagesWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpServiceTasksWriter
import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.shared.CSharpSignalsWriter
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet

/**
 * Emits a C# `FlowNodes` node's own data: `JobType` (a `const`, so it stays attribute-usable), value properties
 * (`CalledProcess`, `Timer`, `Message`, …), the `AttachedTo` link to a boundary event's host, and the nested
 * holders (`Variables`, `Inputs`, `Outputs`). Job types, messages, signals, errors and escalations refer to
 * their shared definition constant. Only `AttachedTo` crosses into another node, and it is expression-bodied
 * so the node's own initialisation never touches another singleton.
 */
internal class CSharpFacetWriter(private val writer: CSharpWriter) {

    fun writeMembers(facets: NodeFacets) {
        facets.jobType?.let { writer.constantExpression("JobType", CSharpServiceTasksWriter.nodeValue(it)) }
    }

    fun writeProperties(facets: NodeFacets) {
        facets.calledProcessId?.let { writer.readonlyProperty(name = "CalledProcess", type = CSharpRuntimeTypes.PROCESS_ID, initializer = "new(${stringLiteral(it)})") }
        facets.timer?.let { writer.readonlyProperty(name = "Timer", type = CSharpRuntimeTypes.BPMN_TIMER, initializer = "new(${CSharpRuntimeTypes.enumMember(CSharpRuntimeTypes.TIMER_TYPE, it.type.name)}, ${stringLiteral(it.expression)})") }
        facets.message?.let { writer.readonlyProperty(name = "Message", type = CSharpRuntimeTypes.MESSAGE_NAME, initializer = "new(${CSharpMessagesWriter.nodeValue(it)})") }
        facets.signal?.let { writer.readonlyProperty(name = "Signal", type = CSharpRuntimeTypes.SIGNAL_NAME, initializer = "new(${CSharpSignalsWriter.nodeValue(it)})") }
        facets.error?.let { writer.readonlyProperty(name = "Error", type = CSharpRuntimeTypes.BPMN_ERROR_DEFINITION, initializer = "new(${CSharpErrorsWriter.nodeArguments(it)})") }
        facets.escalation?.let { writer.readonlyProperty(name = "Escalation", type = CSharpRuntimeTypes.BPMN_ESCALATION_DEFINITION, initializer = "new(${CSharpEscalationsWriter.nodeArguments(it)})") }
        facets.attachedTo?.let { writer.expressionProperty(name = "AttachedTo", type = it.objectName, expression = "${it.objectName}.Instance") }
        facets.isInterrupting?.let { writer.expressionProperty(name = "IsInterrupting", type = "bool", expression = it.toString()) }
    }

    fun writeHolders(facets: NodeFacets) {
        if (facets.variables.isNotEmpty()) {
            writeVariables(facets.variables)
        }
        if (facets.inputs.isNotEmpty()) {
            writeMappings(propertyName = "Inputs", holderName = "InputMappings", mappings = facets.inputs)
        }
        if (facets.outputs.isNotEmpty()) {
            writeMappings(propertyName = "Outputs", holderName = "OutputMappings", mappings = facets.outputs)
        }
    }

    private fun writeVariables(variables: List<VariableFacet>) {
        writer.line()
        writer.readonlyProperty(name = "Variables", type = "NodeVariables", initializer = "new()")
        writer.sealedClass("NodeVariables") {
            variables.forEach { variable ->
                val subtype = "${CSharpRuntimeTypes.VARIABLE_NAME}.${variable.subtype.simpleName}"
                writer.readonlyProperty(name = pascalCase(variable.rawName), type = subtype, initializer = "new(Names.${pascalCase(variable.rawName)})")
            }
            writer.staticClass("Names") {
                variables.forEach { variable -> writer.constant(pascalCase(variable.rawName), variable.rawName) }
            }
        }
    }

    private fun writeMappings(propertyName: String, holderName: String, mappings: List<MappingFacet>) {
        writer.line()
        writer.readonlyProperty(name = propertyName, type = holderName, initializer = "new()")
        writer.sealedClass(holderName) {
            mappings.forEach { mapping ->
                writer.readonlyProperty(name = pascalCase(mapping.target), type = CSharpRuntimeTypes.INPUT_OUTPUT_MAPPING, initializer = mappingInitializer(mapping))
            }
        }
    }

    private fun mappingInitializer(mapping: MappingFacet): String = "new(${stringLiteral(mapping.target)}, ${nullableStringLiteral(mapping.source)}, ${nullableStringLiteral(mapping.sourceExpression)})"
}
