package io.miragon.bpmn.domain

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A process model together with the file it was read from, so a conflict can name its files while [ProcessModel]
 * itself stays free of anything that could leak into generated output.
 */
data class SourcedProcessModel(val fileName: String, val model: ProcessModel) {

    companion object {

        private val logger = KotlinLogging.logger {}

        /**
         * Drops the processes marked non-executable: they get no generated API.
         */
        fun executableOnly(sources: List<SourcedProcessModel>): List<SourcedProcessModel> {
            val (executable, nonExecutable) = sources.partition { it.model.isExecutable }
            nonExecutable.forEach { logger.info { "Skipping '${it.model.processId}' (${it.fileName}): process is marked non-executable" } }
            return executable
        }

        /**
         * Rejects a process id that several files declare. Merging them into variants has to be enabled explicitly.
         */
        fun requireUniqueProcessIds(sources: List<SourcedProcessModel>) {
            val fileNamesByProcessId = sources.groupBy({ it.model.processId }, { it.fileName })
            val duplicate = fileNamesByProcessId.entries.firstOrNull { it.value.size > 1 } ?: return
            throw DuplicateProcessIdException(duplicate.key, duplicate.value)
        }
    }
}
