package io.miragon.bpmn.adapter.outbound.filesystem

import io.github.oshai.kotlinlogging.KotlinLogging
import io.miragon.bpmn.application.port.outbound.LoadBpmnFilesPort
import io.miragon.bpmn.application.port.outbound.LocateBpmnFilesPort
import io.miragon.bpmn.domain.BpmnResource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.streams.toList

internal class BpmnFileLoader :
    LoadBpmnFilesPort,
    LocateBpmnFilesPort {

    private val logger = KotlinLogging.logger {}

    /**
     * Reads every file below the search directory that matches `filePattern`, sorted by relative path.
     */
    override fun loadFrom(baseDirectory: String, filePattern: String): List<BpmnResource> {
        val pattern = BpmnFilePattern.of(baseDirectory, filePattern)
        val searchDir = pattern.searchDirectory
        val matchingFiles = Files.walk(searchDir).use { paths ->
            paths.filter { Files.isRegularFile(it) }.filter { pattern.matches(it) }.toList()
        }
        val files = matchingFiles.sortedBy { relativePathOf(searchDir, it) }

        logger.info { "Found ${files.size} files matching pattern ${pattern.glob} in directory $searchDir" }

        return files.map { file ->
            BpmnResource(fileName = relativePathOf(searchDir, file), content = file.readBytes())
        }
    }

    /**
     * The directory to search for BPMN files:
     * `baseDirectory`, extended by the directories `filePattern` names before its first wildcard.
     */
    override fun resolveSearchDirectory(baseDirectory: String, filePattern: String): Path {
        val pattern = BpmnFilePattern.of(baseDirectory, filePattern)
        return pattern.searchDirectory
    }

    /**
     * Whether the file at [pathInSearchDirectory] is a BPMN file matching `filePattern`. Does not read the file.
     */
    override fun isBpmnFileToLoad(baseDirectory: String, filePattern: String, pathInSearchDirectory: String): Boolean {
        val pattern = BpmnFilePattern.of(baseDirectory, filePattern)
        val file = pattern.searchDirectory.resolve(pathInSearchDirectory)
        return pattern.matches(file) && !isBehindSymlinkedDirectory(file, pattern.searchDirectory)
    }

    /**
     * Files behind a symlinked directory are never loaded, although a build tool walking the directory might find them.
     */
    private fun isBehindSymlinkedDirectory(file: Path, searchDirectory: Path): Boolean {
        val directoriesUpToSearchDirectory = generateSequence(file.parent) { it.parent }.takeWhile { it != searchDirectory }
        return directoriesUpToSearchDirectory.any { Files.isSymbolicLink(it) }
    }

    /**
     * A file's path relative to [searchDir], which is both what the files are sorted by and what names a file in
     * a message.
     *
     * `Files.walk` returns entries in filesystem-dependent order (APFS vs ext4/overlayfs differ),
     * which would leak into the generated code. We use the **relative path** rather than the
     * file name because files legitimately share a file name across directories
     * (e.g. `default/bike-leasing.bpmn`, `corporate/bike-leasing.bpmn`), so the file name
     * neither is a total order nor tells such files apart. Segments are joined with `/` so the path is identical
     * across operating systems, and plain [String] ordering keeps it locale-independent.
     */
    private fun relativePathOf(searchDir: Path, file: Path): String = searchDir.relativize(file).joinToString("/") { it.toString() }
}
