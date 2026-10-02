package io.miragon.bpmn.adapter.outbound.filesystem

import io.github.oshai.kotlinlogging.KotlinLogging
import io.miragon.bpmn.application.port.outbound.SaveProcessApiPort
import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.GeneratedFileHeader
import java.io.File

internal class ProcessApiFileSaver : SaveProcessApiPort {

    private val logger = KotlinLogging.logger {}

    override fun deleteStaleFiles(generatedFiles: List<GeneratedApiFile>, outputFolderPath: String, packagePath: String) {
        val packageDir = packageDirOf(File(outputFolderPath), packagePath)
        val existingFiles = packageDir.listFiles { file -> file.isFile } ?: return
        val generatedFileNames = generatedFiles.map { it.fileName }.toSet()
        val staleFiles = existingFiles.filter { it.name !in generatedFileNames && carriesGeneratedHeader(it) }
        staleFiles.forEach { staleFile ->
            staleFile.delete()
            logger.info { "Removed stale ${staleFile.name} from file-system" }
        }
    }

    override fun writeFiles(generatedFiles: List<GeneratedApiFile>, outputFolderPath: String) {
        val outputFolder = File(outputFolderPath)
        if (!outputFolder.exists()) {
            logger.debug { "Creating output folder: $outputFolderPath" }
            outputFolder.mkdirs()
        }

        generatedFiles.forEach { generatedFile ->
            val packageDir = packageDirOf(outputFolder, generatedFile.packagePath)
            if (!packageDir.exists()) {
                logger.debug { "Creating package folder: ${packageDir.absolutePath}" }
                packageDir.mkdirs()
            }

            val file = File(packageDir, generatedFile.fileName)
            file.writeText(generatedFile.content)

            logger.info { "Generated ${generatedFile.fileName} in file-system" }
        }
    }

    private fun packageDirOf(outputFolder: File, packagePath: String) = File(outputFolder, GeneratedApiFile.packageDirectoryOf(packagePath))

    private fun carriesGeneratedHeader(file: File): Boolean = file.useLines { GeneratedFileHeader.isCarriedBy(it) }
}
