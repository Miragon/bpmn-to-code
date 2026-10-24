package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.ProcessApiNames
import io.miragon.bpmn.domain.validation.SingleModelValidationRule
import io.miragon.bpmn.domain.validation.model.Severity
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext
import io.miragon.bpmn.domain.validation.model.ValidationViolation

/**
 * Rejects element ids whose generated name would shadow a part of the Process API itself, see [ProcessApiNames].
 */
class ReservedElementNameRule : SingleModelValidationRule {

    override val id = "reserved-element-name"
    override val severity = Severity.ERROR
    override val mandatory = true

    override fun validate(context: SingleModelValidationContext): List<ValidationViolation> {
        val model = context.model
        val reservedNodes = model.allFlowNodes.filter { node -> node.id?.let { ProcessApiNames.isReserved(ProcessApiNames.elementName(it)) } == true }
        return reservedNodes.map { node ->
            val elementName = ProcessApiNames.elementName(node.id.orEmpty())
            violation(
                processId = model.processId,
                elementId = node.id,
                message = "Element id '${node.id}' would be generated as '$elementName', which is reserved by the Process API. Rename the element.",
            )
        }
    }
}
