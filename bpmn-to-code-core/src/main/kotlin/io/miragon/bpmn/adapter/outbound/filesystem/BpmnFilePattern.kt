package io.miragon.bpmn.adapter.outbound.filesystem

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.io.path.absolute

/**
 * A `baseDirectory` + `filePattern` pair, resolved into where to search and what to accept:
 * the BPMN files are the regular files below [searchDirectory] that [matches] accepts.
 */
internal class BpmnFilePattern private constructor(val searchDirectory: Path, val glob: String) {

    private val matcher = createMatcher(glob)

    fun matches(file: Path): Boolean = matcher.matches(searchDirectory.relativize(file))

    private fun createMatcher(glob: String): PathMatcher {
        val fs = FileSystems.getDefault()
        val primary = fs.getPathMatcher("glob:$glob")
        if (!glob.startsWith("**/")) return primary
        val rootPattern = glob.removePrefix("**/")
        val rootMatcher = fs.getPathMatcher("glob:$rootPattern")
        return PathMatcher { path -> primary.matches(path) || rootMatcher.matches(path) }
    }

    private data class WildcardCheckResult(val position: Int, val isPresent: Boolean) {
        fun hasNoWildcard() = !isPresent
    }

    companion object {

        fun of(baseDirectory: String, filePattern: String): BpmnFilePattern {
            val basePath = Path.of(baseDirectory).absolute().normalize()
            val (searchDirectory, glob) = resolvePattern(basePath, filePattern)
            return BpmnFilePattern(searchDirectory, glob)
        }

        private fun resolvePattern(basePath: Path, pattern: String): Pair<Path, String> {
            if (!pattern.contains('/')) return basePath to pattern

            val segments = pattern.split('/')
            val wildcard = checkForWildcard(segments)

            return if (wildcard.hasNoWildcard()) {
                val dirPath = segments.dropLast(1).joinToString("/")
                val fileName = segments.last()
                basePath.resolve(dirPath).normalize() to fileName
            } else if (wildcard.position == 0) {
                basePath to pattern
            } else {
                val dirPath = segments.take(wildcard.position).joinToString("/")
                val globPattern = segments.drop(wildcard.position).joinToString("/")
                basePath.resolve(dirPath).normalize() to globPattern
            }
        }

        private fun checkForWildcard(pathSegments: List<String>): WildcardCheckResult {
            val position = pathSegments.indexOfFirst { it.contains('*') }
            return if (position == -1) {
                WildcardCheckResult(position = position, isPresent = false)
            } else {
                WildcardCheckResult(position = position, isPresent = true)
            }
        }
    }
}
