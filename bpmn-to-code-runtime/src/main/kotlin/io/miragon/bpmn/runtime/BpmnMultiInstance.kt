package io.miragon.bpmn.runtime

/**
 * The `multiInstanceLoopCharacteristics` of an activity.
 *
 * [type] tells whether the instances run one after another or in parallel. The collection and element bindings
 * are the engine's expressions as modelled, e.g. `=bikeIds` in Zeebe or `${bikeIds}` in Camunda 7 and Operaton, and
 * `null` where the model declares none. Camunda 7 and Operaton define no output bindings.
 */
data class BpmnMultiInstance(
    val type: MultiInstanceType,
    val inputCollection: String? = null,
    val inputElement: String? = null,
    val outputCollection: String? = null,
    val outputElement: String? = null,
)
