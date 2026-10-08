package io.miragon.bpmn.domain

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A process model together with the file it was read from, so a conflict can name its files while [ProcessModel]
 * itself stays free of anything that could leak into generated output.
 */
data class SourcedProcessModel(val fileName: String, val model: ProcessModel) {

    companion object {

        private val logger = KotlinLogging.logger {}

        private val wellFormedVariantName = Regex("""\p{L}[\p{L}\p{Nd}_-]*""")

        /**
         * Drops the processes marked non-executable: they get no generated API.
         */
        fun executableOnly(sources: List<SourcedProcessModel>): List<SourcedProcessModel> {
            val (executable, nonExecutable) = sources.partition { it.model.isExecutable }
            nonExecutable.forEach { logger.info { "Skipping '${it.model.processId}' (${it.fileName}): process is marked non-executable" } }
            return executable
        }

        /**
         * Rejects a variant name that could not become part of a name, and files that [artifactNameOf] would
         * generate under one name. Every file gets artifacts of its own, so files declaring the same process id
         * need a distinct variant name each.
         */
        fun requireDistinctArtifactNames(sources: List<SourcedProcessModel>, artifactNameOf: (ProcessModel) -> String) {
            val sourcesWithMalformedVariantName = sources.filter { it.hasMalformedVariantName() }
            if (sourcesWithMalformedVariantName.isNotEmpty()) {
                throw ProcessApiNamingException(sourcesWithMalformedVariantName.joinToString("\n") { it.describeMalformedVariantName() })
            }
            val sourcesByArtifactName = sources.groupBy { artifactNameOf(it.model) }
            val collisions = sourcesByArtifactName.filterValues { it.size > 1 }
            if (collisions.isNotEmpty()) {
                throw ProcessApiNamingException(collisions.entries.joinToString("\n") { describeCollision(it.key, it.value) })
            }
        }

        private fun SourcedProcessModel.hasMalformedVariantName(): Boolean {
            val variantName = model.variantName ?: return false
            return !wellFormedVariantName.matches(variantName)
        }

        private fun SourcedProcessModel.describeMalformedVariantName(): String {
            val rejection = "The variantName '${model.variantName}' of $fileName cannot become part of a name."
            return "$rejection It has to start with a letter and may contain letters, digits, '_' and '-'."
        }

        private fun describeCollision(artifactName: String, sources: List<SourcedProcessModel>): String {
            val files = sources.map { it.describe() }.sorted().joinToString()
            return "'$artifactName' would be generated from several BPMN files: $files. " +
                "Every BPMN file gets a Process API of its own: give each a distinct process id, " +
                "or a distinct 'variantName' extension property."
        }

        private fun SourcedProcessModel.describe(): String {
            val variant = model.variantName?.let { ", variantName '$it'" }.orEmpty()
            return "$fileName (process id '${model.processId}'$variant)"
        }
    }
}
