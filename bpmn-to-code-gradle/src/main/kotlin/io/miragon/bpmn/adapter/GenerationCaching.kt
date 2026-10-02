package io.miragon.bpmn.adapter

import io.miragon.bpmn.adapter.inbound.LocateBpmnFilesPlugin
import org.gradle.api.file.Directory
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileTreeElement
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.PathSensitivity

/**
 * Lets Gradle skip a generation that would only produce the same files again.
 * Gradle owns the cache; this file only tells it what to compare.
 *
 * 1. Task is created: [enable] registers the rules. No file is read.
 * 2. Before each run: Gradle calls [ChangeCheck] to find the BPMN files,
 *    then compares their hashes and the task's properties with the last run (kept in `<project>/.gradle`).
 * 3. Nothing differs: the task is skipped as `UP-TO-DATE`.
 *    Something differs: the task runs, the generator reads the BPMN files itself, Gradle stores the new hashes.
 *
 * Gradle and the generator both find the BPMN files through [LocateBpmnFilesPlugin], so both see the same files.
 *
 * The generated directory is an output only below the build directory:
 * there Gradle also notices deleted or edited generated files and can use the build cache.
 * A source folder is not declared, because Gradle fails every task reading a declared output
 * without depending on its producer (`sourcesJar`, linters).
 */
internal class GenerationCaching(private val task: AbstractBpmnTask) {

    fun enable(generatedDirectory: Provider<String>) {
        rerunWhenBpmnFilesChange()
        skipWhileNothingChanged()
        val output = declareAsOutputIfBelowBuildDirectory(generatedDirectory)
        useBuildCacheOnlyWith(output)
    }

    /**
     * Optional: while `baseDir` or `filePattern` is unset the files are unknown, and Gradle reports the unset one.
     */
    private fun rerunWhenBpmnFilesChange() {
        val projectDirectory = task.project.layout.projectDirectory
        val bpmnFiles = task.baseDir.zip(task.filePattern) { baseDir, filePattern ->
            ChangeCheck.bpmnFilesToCompare(projectDirectory, baseDir, filePattern)
        }
        val input = task.inputs.files(bpmnFiles).withPropertyName("bpmnFiles").optional()
        input.withPathSensitivity(PathSensitivity.RELATIVE).ignoreEmptyDirectories()
    }

    /**
     * Without a declared output Gradle never treats a task as up to date unless told so.
     */
    private fun skipWhileNothingChanged() {
        task.outputs.upToDateWhen(Specs.satisfyAll())
    }

    private fun declareAsOutputIfBelowBuildDirectory(generatedDirectory: Provider<String>): Provider<Directory> {
        val layout = task.project.layout
        val directory = layout.projectDirectory.dir(generatedDirectory)
        val output = directory.zip(layout.buildDirectory) { candidate, buildDirectory ->
            ChangeCheck.outputIfBelowBuildDirectory(candidate, buildDirectory)
        }
        task.outputs.dir(output).withPropertyName("generatedDirectory").optional()
        return output
    }

    /**
     * Without a declared output a build cache entry would hold no files.
     */
    private fun useBuildCacheOnlyWith(declaredOutput: Provider<Directory>) {
        task.outputs.cacheIf("The generated directory is declared as output") { ChangeCheck.isOutputDeclared(declaredOutput) }
    }

    /**
     * Called by Gradle, never by this plugin, each time it checks whether the task has to run.
     * Gradle keeps the result of [bpmnFilesToCompare] as a directory plus a filter, not as a list of files,
     * so a BPMN file added later is found even when the configuration cache is reused.
     */
    private object ChangeCheck {

        private val bpmnFileLocator = LocateBpmnFilesPlugin()

        fun bpmnFilesToCompare(projectDirectory: Directory, baseDir: String, filePattern: String): FileTree {
            val absoluteBaseDir = projectDirectory.dir(baseDir).asFile.absolutePath
            val searchDirectory = bpmnFileLocator.resolveSearchDirectory(baseDir = absoluteBaseDir, filePattern = filePattern)
            val filesBelowSearchDirectory = projectDirectory.dir(searchDirectory.toString()).asFileTree
            return filesBelowSearchDirectory.matching { it.include(onlyFilesTheGeneratorReads(absoluteBaseDir, filePattern)) }
        }

        fun outputIfBelowBuildDirectory(generatedDirectory: Directory, buildDirectory: Directory): Directory? {
            val isBelowBuildDirectory = generatedDirectory.asFile.startsWith(buildDirectory.asFile)
            return generatedDirectory.takeIf { isBelowBuildDirectory }
        }

        fun isOutputDeclared(output: Provider<Directory>): Boolean = output.isPresent

        private fun onlyFilesTheGeneratorReads(baseDir: String, filePattern: String) = Spec<FileTreeElement> { entry ->
            val isDirectoryGradleShouldLookInto = entry.isDirectory
            isDirectoryGradleShouldLookInto || bpmnFileLocator.isBpmnFileToLoad(
                baseDir = baseDir,
                filePattern = filePattern,
                pathInSearchDirectory = entry.relativePath.pathString,
            )
        }
    }
}
