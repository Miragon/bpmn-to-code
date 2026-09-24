package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.utils.StringUtils.toCamelCase
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationPhase
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Rejects element ids and variant names whose generated name would shadow a part of the Process API itself.
 *
 * Every element becomes a nested type inside `Flow`, next to the holders (`Next`, `Flows`, `Start`, …), the
 * registries and the runtime types the nodes are built from. An element named like one of those would
 * shadow it in at least one target language and the generated file would not compile; an element whose
 * accessor is named like a `java.lang.Object` method breaks the Java output the same way.
 * A merged model renders each variant as its own `Flow` under `FlowVariants.<Variant>`, so a variant name
 * must not be reserved either, nor match an element of its own variant (Java and C# reject a nested type
 * named like its enclosing type). Runs post-merge, like the collision check it complements.
 */
class ReservedElementNameRule : SingleModelValidationRule {

    override val id = "reserved-element-name"
    override val severity = Severity.ERROR
    override val phase = ValidationPhase.POST_MERGE
    override val mandatory = true

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        val model = context.model
        return findReservedElements(model) + findReservedVariants(model)
    }

    private fun findReservedElements(model: ProcessModel): List<ValidationViolation> = model.allFlowNodes
        .filter { it.id != null && it.getRawName().toCamelCase().isReserved() }
        .map { node ->
            ValidationViolation(
                ruleId = id,
                severity = severity,
                elementId = node.id,
                processId = model.processId,
                message = "Element id '${node.id}' would be generated as '${node.getRawName().toCamelCase()}', which is reserved by the Process API. Rename the element.",
            )
        }

    private fun findReservedVariants(model: ProcessModel): List<ValidationViolation> = model.variants
        .filter { variant -> variant.variantName.toCamelCase().let { it in RESERVED_TYPE_NAMES || it in elementNamesOf(variant) } }
        .map { variant ->
            ValidationViolation(
                ruleId = id,
                severity = severity,
                elementId = null,
                processId = model.processId,
                message = "Variant '${variant.variantName}' would be generated as '${variant.variantName.toCamelCase()}', which is reserved by the Process API or names an element of that variant. Rename the variant.",
            )
        }

    private fun elementNamesOf(variant: ProcessModel.Variant): Set<String> = variant.graph.allFlowNodes
        .map { it.getRawName().toCamelCase() }
        .toSet()

    private fun String.isReserved(): Boolean = this in RESERVED_TYPE_NAMES || replaceFirstChar { it.lowercaseChar() } in RESERVED_ACCESSOR_NAMES

    companion object {

        val RESERVED_TYPE_NAMES: Set<String> = setOf(
            "Flow",
            "Next",
            "Start",
            "Flows",
            "Variables",
            "Inputs",
            "Outputs",
            "Instance",
            "Runtime",
            "Successors",
            "Interior",
            "SequenceFlows",
            "NodeVariables",
            "InputMappings",
            "OutputMappings",
            "ElementId",
            "ProcessId",
            "MessageName",
            "SignalName",
            "VariableName",
            "BpmnTimer",
            "BpmnError",
            "BpmnEscalation",
            "InputOutputMapping",
            "SequenceFlow",
            "FlowNode",
            "AbstractFlowNode",
            "HasSuccessors",
            "HasFlows",
            "FlowScope",
            "BpmnEngine",
            "IFlowNode",
            "ISequenceFlow",
            "Messages",
            "Errors",
            "Signals",
            "Escalations",
            "ServiceTasks",
            "FlowVariants",
        )

        val RESERVED_ACCESSOR_NAMES: Set<String> = setOf(
            "hashCode",
            "toString",
            "getClass",
            "notify",
            "notifyAll",
            "wait",
            "equals",
        )
    }
}
