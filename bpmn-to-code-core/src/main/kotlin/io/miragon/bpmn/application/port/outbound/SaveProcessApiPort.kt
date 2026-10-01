package io.miragon.bpmn.application.port.outbound

import io.miragon.bpmn.domain.GeneratedApiFile

interface SaveProcessApiPort {
    fun deleteStaleFiles(generatedFiles: List<GeneratedApiFile>, outputFolderPath: String, packagePath: String)

    fun writeFiles(generatedFiles: List<GeneratedApiFile>, outputFolderPath: String)
}
