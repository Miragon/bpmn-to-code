package io.miragon.bpmn.web.model

import io.miragon.bpmn.domain.BpmnReadException
import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * One uploaded BPMN file, as the generation endpoint receives it.
 */
@Serializable
data class BpmnFileData(
    /**
     * The name of the uploaded file.
     */
    val fileName: String,

    /**
     * The BPMN XML encoded in Base64.
     */
    val content: String,
) {

    fun bpmnXml(): String = try {
        String(Base64.getDecoder().decode(content))
    } catch (e: IllegalArgumentException) {
        throw BpmnReadException("File '$fileName' is not valid Base64", e)
    }
}
