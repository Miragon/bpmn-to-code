package io.miragon.bpmn.application.port.inbound

import java.nio.file.Path

interface LocateBpmnFilesQuery {

    /**
     * The directory to search for BPMN files:
     * `baseDir`, extended by the directories `filePattern` names before its first wildcard.
     */
    fun resolveSearchDirectory(criteria: Criteria): Path

    /**
     * Whether the file at [pathInSearchDirectory], a path relative to the search directory, matches `filePattern`.
     */
    fun isBpmnFileToLoad(criteria: Criteria, pathInSearchDirectory: String): Boolean

    data class Criteria(val baseDir: String, val filePattern: String)
}
