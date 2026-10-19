package io.miragon.bpmn.adapter.outbound.codegen.builder.typescript

import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.nameAndCode
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.nullableStringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptCodeFormat.stringLiteral
import io.miragon.bpmn.adapter.outbound.codegen.builder.typescript.TypeScriptFlowWriter.Companion.instanceOf
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.MappingFacet
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.NodeFacets
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.SharedValue
import io.miragon.bpmn.adapter.outbound.codegen.flow.FlowGraph.VariableFacet

internal class TypeScriptFacetWriter(private val writer: TypeScriptWriter) {

    fun writeFields(facets: NodeFacets) {
        facets.jobType?.let { writer.field("jobType", constantOrString(SharedDefinitionType.SERVICE_TASKS, it)) }
        facets.calledProcessId?.let { writer.field("calledProcess", stringLiteral(it)) }
        facets.timer?.let { timer ->
            val type = stringLiteral(timer.type.name)
            writer.field("timer", "{ type: $type, timerValue: ${stringLiteral(timer.expression)} } as const")
        }
        facets.message?.let { writer.field("message", constantOrString(SharedDefinitionType.MESSAGES, it)) }
        facets.signal?.let { writer.field("signal", constantOrString(SharedDefinitionType.SIGNALS, it)) }
        facets.error?.let { writer.field("error", constantOrNameAndCode(SharedDefinitionType.ERRORS, it)) }
        facets.escalation?.let { writer.field("escalation", constantOrNameAndCode(SharedDefinitionType.ESCALATIONS, it)) }
        facets.isInterrupting?.let { writer.field("isInterrupting", it.toString()) }
        if (facets.throwsCompensation) {
            writer.field("throwsCompensation", "true")
        }
        facets.attachedTo?.let { host ->
            writer.line()
            writer.getter(name = "attachedTo", type = host.objectName, expression = instanceOf(host.objectName))
        }
    }

    fun writeHolders(facets: NodeFacets) {
        writeVariables(facets.variables)
        writeMappings(holderName = "Inputs", mappings = facets.inputs)
        writeMappings(holderName = "Outputs", mappings = facets.outputs)
    }

    private fun writeVariables(variables: List<VariableFacet>) {
        if (variables.isEmpty()) {
            return
        }
        writer.line()
        writer.objectField(name = "variables", prefix = "${writer.imports.runtimeValue("variableDefinitions")}(", suffix = ")") {
            variables.forEach { variable ->
                val name = writer.imports.sharedReference(SharedDefinitionType.PROCESS_VARIABLES, variable.constantName)
                val direction = variable.subtype.simpleName.replaceFirstChar { it.lowercaseChar() }
                writer.property(variable.constantName, "${writer.imports.runtimeValue(direction)}($name)")
            }
        }
    }

    private fun writeMappings(holderName: String, mappings: List<MappingFacet>) {
        if (mappings.isEmpty()) {
            return
        }
        writer.line()
        writer.objectField(name = holderName, suffix = " as const") {
            mappings.forEach { mapping ->
                val target = stringLiteral(mapping.target)
                val source = nullableStringLiteral(mapping.source)
                val sourceExpression = nullableStringLiteral(mapping.sourceExpression)
                writer.property(mapping.constantName, "{ target: $target, source: $source, sourceExpression: $sourceExpression }")
            }
        }
    }

    private fun constantOrString(type: SharedDefinitionType, shared: SharedValue<String>): String {
        val constant = shared.constant ?: return stringLiteral(shared.value)
        return writer.imports.sharedReference(type, constant.name)
    }

    private fun constantOrNameAndCode(type: SharedDefinitionType, shared: SharedValue<Pair<String, String>>): String {
        val constant = shared.constant
        if (constant != null) {
            return writer.imports.sharedReference(type, constant.name)
        }
        val (name, code) = shared.value
        return "${nameAndCode(name, code)} as const"
    }
}
