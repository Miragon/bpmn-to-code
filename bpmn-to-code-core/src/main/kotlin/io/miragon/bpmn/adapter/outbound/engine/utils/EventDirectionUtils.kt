package io.miragon.bpmn.adapter.outbound.engine.utils

import io.miragon.bpmn.domain.shared.EventDirection

/**
 * Maps a BPMN event element type name to its throw/catch role, shared by message and signal extraction.
 * End / intermediate-throw events send; start / intermediate-catch / boundary events receive. Any other
 * element type carries no throw/catch role and yields `null`.
 */
object EventDirectionUtils {

    fun fromElementTypeName(typeName: String): EventDirection? {
        return when (typeName) {
            "endEvent", "intermediateThrowEvent" -> EventDirection.THROW
            "startEvent", "intermediateCatchEvent", "boundaryEvent" -> EventDirection.CATCH
            else -> null
        }
    }
}
