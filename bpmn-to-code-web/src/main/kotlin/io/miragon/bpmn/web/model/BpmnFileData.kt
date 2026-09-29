package io.miragon.bpmn.web.model

import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * One uploaded BPMN file, as both generation endpoints receive it.
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

    fun bpmnXml(): String = String(Base64.getDecoder().decode(content))

    fun processName(): String = fileName.removeSuffix(".bpmn")
}
