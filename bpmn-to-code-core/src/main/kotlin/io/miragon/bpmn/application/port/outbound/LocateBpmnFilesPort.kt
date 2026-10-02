package io.miragon.bpmn.application.port.outbound

import java.nio.file.Path

interface LocateBpmnFilesPort {
    fun resolveSearchDirectory(baseDirectory: String, filePattern: String): Path

    fun isBpmnFileToLoad(baseDirectory: String, filePattern: String, pathInSearchDirectory: String): Boolean
}
