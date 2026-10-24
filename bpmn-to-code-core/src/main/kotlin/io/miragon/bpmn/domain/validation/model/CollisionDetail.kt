package io.miragon.bpmn.domain.validation.model

/**
 * Different ids (e.g. "endEvent_dataProcessed" and "endEvent-dataProcessed") that would be generated under the same
 * name (e.g. "EndEventDataProcessed"), as the collision rules report them.
 */
internal data class CollisionDetail(
    val variableType: String,
    val constantName: String,
    val conflictingIds: List<String>,
    val processId: String,
) {

    fun describe(): String = "[$variableType] '$constantName' has conflicting IDs: ${conflictingIds.joinToString(", ")}"
}
