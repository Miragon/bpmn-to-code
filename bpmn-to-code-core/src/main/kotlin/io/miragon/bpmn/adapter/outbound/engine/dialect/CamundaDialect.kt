package io.miragon.bpmn.adapter.outbound.engine.dialect

import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.extractAttribute
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.filterByType
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.findExtensionElements
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.withAttribute
import io.miragon.bpmn.adapter.outbound.engine.xml.CamundaXmlApi.withElementName
import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.IoMapping
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.VariableDefinition
import io.miragon.bpmn.domain.shared.VariableDirection
import io.miragon.bpmn.domain.utils.StringUtils.removeExpressionSyntax
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants
import org.camunda.bpm.model.bpmn.instance.CallActivity
import org.camunda.bpm.model.bpmn.instance.FlowNode
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics
import org.camunda.bpm.model.bpmn.instance.ServiceTask
import org.camunda.bpm.model.xml.instance.DomElement
import org.camunda.bpm.model.xml.instance.ModelElementInstance

/**
 * Reads the Camunda-7-style half of a BPMN model. Camunda 7 and Operaton share the identical element and
 * attribute vocabulary and differ only in their XML namespace, so both engines use this reader with their
 * own [namespace] (see ADR 010).
 */
@Suppress("TooManyFunctions")
internal class CamundaDialect(override val namespace: String) : EngineDialect {

    /**
     * Empty on purpose. Camunda's extension vocabulary is richer than what the typed fields capture:
     * `camunda:inputParameter` may nest a `camunda:script`, `camunda:map` or `camunda:list` where only
     * the text body is read; `camunda:in`/`out` carry `businessKey` and `local` beyond the mapping; and
     * `camunda:properties` is read for two specific property names only. Reporting these raw is what
     * keeps that information reachable.
     */
    override val fullyReadExtensions = emptySet<String>()

    /**
     * Service tasks and message throw events are both `camunda:ServiceTaskLike`. A service task always
     * reports an implementation — [TaskImplementation.Unspecified] when nothing is configured, so the
     * missing-implementation rule can flag it — while other nodes only do when one is actually set.
     */
    override fun implementationOf(node: FlowNode): TaskImplementation? {
        if (node is ServiceTask) return node.attributeImplementation()?.implementation ?: TaskImplementation.Unspecified
        return node.getChildElementsByType(MessageEventDefinition::class.java)
            .firstNotNullOfOrNull { it.attributeImplementation() }
            ?.implementation
    }

    override fun fullyReadAttributesOf(node: FlowNode): Set<String> {
        val resolved = when (node) {
            is ServiceTask -> node.attributeImplementation()
            else -> node.getChildElementsByType(MessageEventDefinition::class.java)
                .firstNotNullOfOrNull { it.attributeImplementation() }
        }
        return setOfNotNull(resolved?.attributeName)
    }

    override fun ioMappingOf(node: FlowNode): IoMapping? {
        val parameters = node.findExtensionElements()
            .filterByType(BpmnModelConstants.CAMUNDA_ELEMENT_INPUT_OUTPUT)
            .flatMap { it.domElement.childElements }
        val inputs = parameters.withElementName(BpmnModelConstants.CAMUNDA_ELEMENT_INPUT_PARAMETER).mapNotNull { it.toParameter() }
        val outputs = parameters.withElementName(BpmnModelConstants.CAMUNDA_ELEMENT_OUTPUT_PARAMETER).mapNotNull { it.toParameter() }
        return IoMapping(inputs, outputs).takeUnless { it.isEmpty() }
    }

    override fun multiInstanceBindingsOf(
        loop: MultiInstanceLoopCharacteristics,
        base: MultiInstanceDefinition,
    ): MultiInstanceDefinition {
        return base.copy(
            inputCollection = loop.attribute(CamundaModelConstants.COLLECTION_ATTRIBUTE),
            inputElement = loop.attribute(CamundaModelConstants.ELEMENT_VARIABLE_ATTRIBUTE),
        )
    }

    override fun variablesOf(node: FlowNode): List<VariableDefinition> {
        val extensions = node.findExtensionElements()
        val ioMapping = ioMappingOf(node)
        val ioVariables = ioMapping?.inputs.orEmpty().map { Triple(it.target, VariableDirection.INPUT, it.source) } +
            ioMapping?.outputs.orEmpty().map { Triple(it.target, VariableDirection.OUTPUT, it.source) }
        val allVariables = ioVariables +
            node.multiInstanceVariables() +
            extensions.callActivityMappingVariables() +
            extensions.additionalVariables()
        return allVariables
            .map { (name, direction, expression) -> Triple(name.removeExpressionSyntax(), direction, expression) }
            .distinct()
            .map { (name, direction, expression) -> VariableDefinition(name, direction, expression) }
    }

    override fun callActivityOf(callActivity: CallActivity): CallActivityDefinition {
        val extensions = callActivity.findExtensionElements()
        return CallActivityDefinition(
            id = callActivity.id,
            calledElement = callActivity.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_CALLED_ELEMENT),
            mappings = extensions.toCallActivityMappings(),
            propagateAllInputVariables = extensions.propagatesAll(BpmnModelConstants.CAMUNDA_ELEMENT_IN),
            propagateAllOutputVariables = extensions.propagatesAll(BpmnModelConstants.CAMUNDA_ELEMENT_OUT),
        )
    }

    /**
     * The implementation attributes in precedence order. Declared once so the resolved value and the
     * name of the attribute it came from can never disagree.
     */
    private val implementationAttributes: List<Pair<String, (String) -> TaskImplementation>> = listOf(
        BpmnModelConstants.CAMUNDA_ATTRIBUTE_TOPIC to { value -> TaskImplementation.ExternalTask(value) },
        BpmnModelConstants.CAMUNDA_ATTRIBUTE_DELEGATE_EXPRESSION to { value -> TaskImplementation.DelegateExpression(value) },
        BpmnModelConstants.CAMUNDA_ATTRIBUTE_CLASS to { value -> TaskImplementation.JavaClass(value) },
        BpmnModelConstants.CAMUNDA_ATTRIBUTE_EXPRESSION to { value -> TaskImplementation.Expression(value) },
    )

    private data class ResolvedImplementation(val attributeName: String, val implementation: TaskImplementation)

    private fun ModelElementInstance.attributeImplementation(): ResolvedImplementation? {
        return implementationAttributes.firstNotNullOfOrNull { (name, build) ->
            attribute(name)?.let { ResolvedImplementation(name, build(it)) }
        }
    }

    private fun ModelElementInstance.attribute(name: String): String? {
        return getAttributeValueNs(namespace, name)?.takeIf { it.isNotBlank() }
    }

    private fun FlowNode.multiInstanceVariables(): List<Triple<String, VariableDirection, String?>> {
        return getChildElementsByType(MultiInstanceLoopCharacteristics::class.java)
            .flatMap { loop ->
                listOfNotNull(
                    loop.attribute(CamundaModelConstants.COLLECTION_ATTRIBUTE),
                    loop.attribute(CamundaModelConstants.ELEMENT_VARIABLE_ATTRIBUTE),
                )
            }
            .map { Triple(it, VariableDirection.INPUT, it) }
    }

    private fun List<ModelElementInstance>.callActivityMappingVariables(): List<Triple<String, VariableDirection, String?>> {
        val inElements = filterByType(BpmnModelConstants.CAMUNDA_ELEMENT_IN)
        val outElements = filterByType(BpmnModelConstants.CAMUNDA_ELEMENT_OUT)
        val sources = inElements.extractAttribute(BpmnModelConstants.CAMUNDA_ATTRIBUTE_SOURCE)
        val sourceExpressions = inElements.extractAttribute(BpmnModelConstants.CAMUNDA_ATTRIBUTE_SOURCE_EXPRESSION)
        val targets = outElements.extractAttribute(BpmnModelConstants.CAMUNDA_ATTRIBUTE_TARGET)
        return (sources + sourceExpressions).map { Triple(it, VariableDirection.INPUT, it) } +
            targets.map { Triple(it, VariableDirection.OUTPUT, it) }
    }

    private fun List<ModelElementInstance>.additionalVariables(): List<Triple<String, VariableDirection, String?>> {
        val properties = filterByType(BpmnModelConstants.CAMUNDA_ELEMENT_PROPERTIES)
            .flatMap { it.domElement.childElements }
            .withElementName(BpmnModelConstants.CAMUNDA_ELEMENT_PROPERTY)
        val inputs = properties.valuesOfProperty(CamundaModelConstants.ADDITIONAL_INPUT_VARIABLES_PROPERTY_NAME)
        val outputs = properties.valuesOfProperty(CamundaModelConstants.ADDITIONAL_OUTPUT_VARIABLES_PROPERTY_NAME)
        return inputs.map { Triple(it, VariableDirection.INPUT, null) } +
            outputs.map { Triple(it, VariableDirection.OUTPUT, null) }
    }

    private fun List<DomElement>.valuesOfProperty(propertyName: String): List<String> {
        return withAttribute(BpmnModelConstants.CAMUNDA_ATTRIBUTE_NAME to propertyName)
            .mapNotNull { it.getAttribute(BpmnModelConstants.CAMUNDA_ATTRIBUTE_VALUE) }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun List<ModelElementInstance>.toCallActivityMappings(): List<CallActivityDefinition.Mapping> {
        val inputs = filterByType(BpmnModelConstants.CAMUNDA_ELEMENT_IN)
            .mapNotNull { it.domElement.toCallActivityMapping(VariableDirection.INPUT) }
        val outputs = filterByType(BpmnModelConstants.CAMUNDA_ELEMENT_OUT)
            .mapNotNull { it.domElement.toCallActivityMapping(VariableDirection.OUTPUT) }
        return inputs + outputs
    }

    private fun List<ModelElementInstance>.propagatesAll(elementType: String): Boolean? {
        val propagatesAll = filterByType(elementType).any {
            it.domElement.getAttribute(CamundaModelConstants.VARIABLES_ATTRIBUTE) == CamundaModelConstants.VARIABLES_ALL_VALUE
        }
        return if (propagatesAll) true else null
    }

    private fun DomElement.toCallActivityMapping(direction: VariableDirection): CallActivityDefinition.Mapping? {
        val source = getAttribute(BpmnModelConstants.CAMUNDA_ATTRIBUTE_SOURCE)?.takeIf { it.isNotBlank() }
        val sourceExpression = getAttribute(BpmnModelConstants.CAMUNDA_ATTRIBUTE_SOURCE_EXPRESSION)?.takeIf { it.isNotBlank() }
        val target = getAttribute(BpmnModelConstants.CAMUNDA_ATTRIBUTE_TARGET)?.takeIf { it.isNotBlank() }
        if (source == null && sourceExpression == null && target == null) return null
        return CallActivityDefinition.Mapping(direction, source, sourceExpression, target)
    }

    /**
     * `camunda:inputParameter` / `camunda:outputParameter` carry the variable name in their `name`
     * attribute and the bound expression as their text content, e.g. `${'$'}{execution.getVariable('x')}`.
     */
    private fun DomElement.toParameter(): IoMapping.Parameter? {
        val target = getAttribute(BpmnModelConstants.CAMUNDA_ATTRIBUTE_NAME)?.takeIf { it.isNotBlank() } ?: return null
        val source = textContent?.trim()?.takeIf { it.isNotBlank() }
        return IoMapping.Parameter(target = target, source = source)
    }
}
