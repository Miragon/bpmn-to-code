package io.miragon.bpmn.domain.shared

/**
 * `bpmn:multiInstanceLoopCharacteristics` on an activity, normalised across engines.
 *
 * [sequential] comes from the standard `isSequential` attribute and decides whether the instances run one
 * after another or concurrently. The collection/element bindings come from `zeebe:loopCharacteristics`
 * (Zeebe) or `camunda:collection` / `camunda:elementVariable` (Camunda 7 / Operaton); [cardinality] and
 * [completionCondition] are the standard `loopCardinality` / `completionCondition` expressions.
 */
data class MultiInstanceDefinition(
    val sequential: Boolean = false,
    val inputCollection: String? = null,
    val inputElement: String? = null,
    val outputCollection: String? = null,
    val outputElement: String? = null,
    val cardinality: String? = null,
    val completionCondition: String? = null,
)
