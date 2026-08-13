package io.miragon.bpmn.adapter.outbound.engine.dialect

/**
 * Extends [org.camunda.bpm.model.bpmn.impl.BpmnModelConstants] with additional constants,
 * relevant for engines building up on Camunda 7
 */
internal object CamundaModelConstants {

    const val ADDITIONAL_INPUT_VARIABLES_PROPERTY_NAME = "additionalInputVariables"
    const val ADDITIONAL_OUTPUT_VARIABLES_PROPERTY_NAME = "additionalOutputVariables"

    const val VARIABLES_ATTRIBUTE = "variables"
    const val VARIABLES_ALL_VALUE = "all"

    const val COLLECTION_ATTRIBUTE = "collection"
    const val ELEMENT_VARIABLE_ATTRIBUTE = "elementVariable"
}