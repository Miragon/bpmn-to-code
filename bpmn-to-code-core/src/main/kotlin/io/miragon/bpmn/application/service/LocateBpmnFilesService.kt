package io.miragon.bpmn.application.service

import io.miragon.bpmn.adapter.outbound.filesystem.BpmnFileLoader
import io.miragon.bpmn.application.port.inbound.LocateBpmnFilesQuery
import io.miragon.bpmn.application.port.outbound.LocateBpmnFilesPort
import java.nio.file.Path

class LocateBpmnFilesService(private val bpmnFileLocator: LocateBpmnFilesPort = BpmnFileLoader()) : LocateBpmnFilesQuery {

    override fun resolveSearchDirectory(criteria: LocateBpmnFilesQuery.Criteria): Path = bpmnFileLocator.resolveSearchDirectory(
        baseDirectory = criteria.baseDir,
        filePattern = criteria.filePattern,
    )

    override fun isBpmnFileToLoad(criteria: LocateBpmnFilesQuery.Criteria, pathInSearchDirectory: String): Boolean = bpmnFileLocator.isBpmnFileToLoad(
        baseDirectory = criteria.baseDir,
        filePattern = criteria.filePattern,
        pathInSearchDirectory = pathInSearchDirectory,
    )
}
