package io.miragon.bpmn.adapter.inbound

import io.miragon.bpmn.application.port.inbound.LocateBpmnFilesQuery
import io.miragon.bpmn.application.service.LocateBpmnFilesService
import java.nio.file.Path

/**
 * Tells a build tool which files the generation and validation plugins load
 * for a `baseDir` + `filePattern`, without reading them.
 */
class LocateBpmnFilesPlugin(private val query: LocateBpmnFilesQuery = LocateBpmnFilesService()) {

    /**
     * The directory to search for BPMN files:
     * `baseDir`, extended by the directories `filePattern` names before its first wildcard.
     * Example: `/project` + `src/main/resources/order-*.bpmn` gives `/project/src/main/resources`.
     */
    fun resolveSearchDirectory(baseDir: String, filePattern: String): Path {
        val criteria = LocateBpmnFilesQuery.Criteria(baseDir = baseDir, filePattern = filePattern)
        return query.resolveSearchDirectory(criteria)
    }

    /**
     * Whether the file at [pathInSearchDirectory], a path relative to the search directory, matches `filePattern`.
     * Example: with the pattern above `order-1.bpmn` does, `notes.txt` and `drafts/order-2.bpmn` do not.
     */
    fun isBpmnFileToLoad(baseDir: String, filePattern: String, pathInSearchDirectory: String): Boolean {
        val criteria = LocateBpmnFilesQuery.Criteria(baseDir = baseDir, filePattern = filePattern)
        return query.isBpmnFileToLoad(criteria, pathInSearchDirectory)
    }
}
