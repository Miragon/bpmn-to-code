package io.miragon.bpmn.adapter.outbound.engine.dialect

import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.filterByType
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.findExtensionElement
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.findExtensionElements
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.findExtensionElementsWithType
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.findFirstByType
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.nonBlankAttribute
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.nonBlankAttributeNs
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.IoMapping
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants
import org.camunda.bpm.model.bpmn.instance.CallActivity
import org.camunda.bpm.model.bpmn.instance.FlowNode
import org.camunda.bpm.model.bpmn.instance.Message
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics
import org.camunda.bpm.model.xml.instance.DomElement
import org.camunda.bpm.model.xml.instance.ModelElementInstance

/**
 * Reads the `zeebe:` half of a BPMN model — Camunda 8 / Zeebe extension elements.
 */
internal class ZeebeDialect : EngineDialect {

    override val namespace = ZeebeModelConstants.NAMESPACE

    /**
     * Zeebe's vocabulary for these four is small enough that the typed fields cover it completely:
     * `taskDefinition` (type, retries), `ioMapping` (source/target per parameter), `loopCharacteristics`
     * (both collections and both element variables) and `calledElement` (process id and both propagate
     * flags).
     */
    override val fullyReadExtensions = setOf(
        ZeebeModelConstants.ELEMENT_TASK_DEFINITION,
        ZeebeModelConstants.ELEMENT_IO_MAPPING,
        ZeebeModelConstants.ELEMENT_LOOP_CHARACTERISTICS,
        BpmnModelConstants.BPMN_ATTRIBUTE_CALLED_ELEMENT,
    )

    /**
     * `zeebe:modelerTemplate` reaches [TaskImplementation.Connector] only when the node also has a
     * `zeebe:taskDefinition`; on its own it is not read, so it is not claimed here either.
     */
    override fun fullyReadAttributesOf(node: FlowNode): Set<String> = when (implementationOf(node)) {
        is TaskImplementation.Connector -> setOf(ZeebeModelConstants.ATTRIBUTE_MODELER_TEMPLATE)
        else -> emptySet()
    }

    override fun implementationOf(node: FlowNode): TaskImplementation? {
        val taskDefinition = node.findExtensionElements().findFirstByType(ZeebeModelConstants.ELEMENT_TASK_DEFINITION)
            ?: return null
        val jobType = taskDefinition.nonBlankAttribute(BpmnModelConstants.BPMN_ATTRIBUTE_TYPE)
            ?: return TaskImplementation.Unspecified
        val retries = taskDefinition.nonBlankAttribute(ZeebeModelConstants.ATTRIBUTE_RETRIES)
        val template = node.nonBlankAttributeNs(ZeebeModelConstants.NAMESPACE, ZeebeModelConstants.ATTRIBUTE_MODELER_TEMPLATE)
        return when (template) {
            null -> TaskImplementation.JobWorker(jobType, retries)
            else -> TaskImplementation.Connector(jobType, template, retries)
        }
    }

    override fun ioMappingOf(node: FlowNode): IoMapping? {
        val parameters = node.findExtensionElementsWithType(ZeebeModelConstants.ELEMENT_IO_MAPPING)
            .flatMap { it.domElement.childElements }
        val inputs = parameters.filter { it.localName == ZeebeModelConstants.ELEMENT_INPUT }.mapNotNull { it.toParameter() }
        val outputs = parameters.filter { it.localName == ZeebeModelConstants.ELEMENT_OUTPUT }.mapNotNull { it.toParameter() }
        return IoMapping(inputs, outputs).takeUnless { it.isEmpty() }
    }

    override fun multiInstanceBindingsOf(
        loop: MultiInstanceLoopCharacteristics,
        base: MultiInstanceDefinition,
    ): MultiInstanceDefinition {
        val characteristics = loop.findExtensionElements()
            .filterByType(ZeebeModelConstants.ELEMENT_LOOP_CHARACTERISTICS)
            .firstOrNull() ?: return base
        return base.copy(
            inputCollection = characteristics.nonBlankAttribute(ZeebeModelConstants.ATTRIBUTE_INPUT_COLLECTION),
            inputElement = characteristics.nonBlankAttribute(ZeebeModelConstants.ATTRIBUTE_INPUT_ELEMENT),
            outputCollection = characteristics.nonBlankAttribute(ZeebeModelConstants.ATTRIBUTE_OUTPUT_COLLECTION),
            outputElement = characteristics.nonBlankAttribute(ZeebeModelConstants.ATTRIBUTE_OUTPUT_ELEMENT),
        )
    }

    override fun variablesOf(node: FlowNode): List<VariableDefinition> {
        val ioMapping = ioMappingOf(node)
        val inputs = ioMapping?.inputs.orEmpty().map { Triple(it.target, VariableDirection.INPUT, it.source) }
        val outputs = ioMapping?.outputs.orEmpty().map { Triple(it.target, VariableDirection.OUTPUT, it.source) }
        val loopVariables = node.multiInstanceVariables()
        return (inputs + outputs + loopVariables)
            .distinct()
            .map { (name, direction, expression) -> VariableDefinition(name, direction, expression) }
    }

    override fun callActivityOf(callActivity: CallActivity): CallActivityDefinition {
        val calledElement = callActivity.findExtensionElement(BpmnModelConstants.BPMN_ATTRIBUTE_CALLED_ELEMENT)
        val mappings = ioMappingOf(callActivity)
        return CallActivityDefinition(
            id = callActivity.id,
            calledElement = calledElement?.getAttributeValue(ZeebeModelConstants.ATTRIBUTE_PROCESS_ID),
            mappings = mappings.toCallActivityMappings(),
            propagateAllInputVariables = calledElement?.propagateFlag(ZeebeModelConstants.ATTRIBUTE_PROPAGATE_PARENT),
            propagateAllOutputVariables = calledElement?.propagateFlag(ZeebeModelConstants.ATTRIBUTE_PROPAGATE_CHILD),
        )
    }

    override fun correlationKeyOf(message: Message): String? {
        val subscription = message.findExtensionElementsWithType(ZeebeModelConstants.ELEMENT_SUBSCRIPTION).firstOrNull()
        return subscription?.getAttributeValue(ZeebeModelConstants.ATTRIBUTE_CORRELATION_KEY)
    }

    private fun IoMapping?.toCallActivityMappings(): List<CallActivityDefinition.Mapping> {
        val inputs = this?.inputs.orEmpty().map {
            CallActivityDefinition.Mapping(direction = VariableDirection.INPUT, source = it.source, target = it.target)
        }
        val outputs = this?.outputs.orEmpty().map { CallActivityDefinition.Mapping(VariableDirection.OUTPUT, source = it.source, target = it.target) }
        return inputs + outputs
    }

    /**
     * Multi-instance collections and element variables are process variables too. The `=`-prefixed FEEL
     * form is the declaration; the variable name itself is the expression without that prefix.
     */
    private fun FlowNode.multiInstanceVariables(): List<Triple<String, VariableDirection, String?>> {
        val characteristics = getChildElementsByType(MultiInstanceLoopCharacteristics::class.java)
            .flatMap { it.findExtensionElements() }
            .filterByType(ZeebeModelConstants.ELEMENT_LOOP_CHARACTERISTICS)
        val inputs = characteristics.attributeValues(
            ZeebeModelConstants.ATTRIBUTE_INPUT_ELEMENT,
            ZeebeModelConstants.ATTRIBUTE_INPUT_COLLECTION,
        )
        val outputs = characteristics.attributeValues(
            ZeebeModelConstants.ATTRIBUTE_OUTPUT_ELEMENT,
            ZeebeModelConstants.ATTRIBUTE_OUTPUT_COLLECTION,
        )
        return inputs.map { Triple(it.removePrefix("="), VariableDirection.INPUT, it) } +
            outputs.map { Triple(it.removePrefix("="), VariableDirection.OUTPUT, it) }
    }

    private fun List<ModelElementInstance>.attributeValues(vararg names: String): List<String> = names.flatMap { name -> mapNotNull { it.domElement.getAttribute(name) } }

    private fun DomElement.toParameter(): IoMapping.Parameter? {
        val target = getAttribute(ZeebeModelConstants.ATTRIBUTE_TARGET)?.takeIf { it.isNotBlank() } ?: return null
        val source = getAttribute(ZeebeModelConstants.ATTRIBUTE_SOURCE)?.takeIf { it.isNotBlank() }
        return IoMapping.Parameter(target = target, source = source)
    }

    private fun ModelElementInstance.propagateFlag(attribute: String): Boolean? = getAttributeValue(attribute)?.takeIf { it.isNotBlank() }?.toBooleanStrictOrNull()
}
