package io.miragon.bpmn.benchmark

/**
 * One generator input.
 * A [heavy] scenario would take too long without the JIT, so it is skipped in interpreted mode.
 */
data class Scenario(val name: String, val engine: String, val models: List<BpmnModelFile>, val heavy: Boolean = false) {

    data class BpmnModelFile(val name: String, val xml: String)

    /**
     * The inputs every generator is measured with.
     * They stick to models the baseline accepts too:
     * 5.2.0 rejects the catch-all error boundary event of `cancel-bike-order`, so that model is left out.
     */
    companion object {
        const val WEB_SAMPLE = "bike-leasing"

        private const val MANY_FILES_COUNT = 100

        private val processIdPattern = Regex("""<bpmn:process id="([^"]+)"""")

        fun webSample(): Scenario {
            val bikeLeasing = shared("zeebe/bike-leasing.bpmn")
            return Scenario(name = WEB_SAMPLE, engine = "ZEEBE", models = listOf(bikeLeasing))
        }

        fun all(): List<Scenario> {
            val webMaximum = listOf(
                shared("zeebe/bike-leasing.bpmn"),
                shared("zeebe/membership.bpmn"),
                shared("zeebe/welcome-package.bpmn"),
            )
            val camunda7Sample = listOf(shared("c7/bike-leasing.bpmn"))
            return listOf(
                webSample(),
                Scenario(name = "bike-leasing-c7", engine = "CAMUNDA_7", models = camunda7Sample),
                Scenario(name = "3-files", engine = "ZEEBE", models = webMaximum),
                Scenario(name = "$MANY_FILES_COUNT-files", engine = "ZEEBE", models = copies(webMaximum), heavy = true),
                synthetic(nodeCount = 250, heavy = false),
                synthetic(nodeCount = 1000, heavy = true),
                synthetic(nodeCount = 2000, heavy = true),
            )
        }

        fun named(name: String): Scenario = all().first { it.name == name }

        private fun synthetic(nodeCount: Int, heavy: Boolean): Scenario {
            val model = SyntheticProcess(nodeCount).toModelFile()
            return Scenario(name = "synthetic-$nodeCount", engine = "ZEEBE", models = listOf(model), heavy = heavy)
        }

        private fun copies(models: List<BpmnModelFile>): List<BpmnModelFile> = (1..MANY_FILES_COUNT).map { index ->
            withRenamedProcess(model = models[index % models.size], suffix = "Copy$index")
        }

        private fun withRenamedProcess(model: BpmnModelFile, suffix: String): BpmnModelFile {
            val processId = processIdPattern.find(model.xml)?.groupValues?.get(1)
            checkNotNull(processId) { "${model.name} declares no process" }
            val renamedXml = model.xml.replace("\"$processId\"", "\"$processId$suffix\"")
            return BpmnModelFile(name = "${model.name}-$suffix", xml = renamedXml)
        }

        private fun shared(path: String): BpmnModelFile {
            val resource = "bpmn/$path"
            val stream = Scenario::class.java.classLoader.getResourceAsStream(resource)
            checkNotNull(stream) { "Missing shared model $resource" }
            val xml = stream.use { it.readBytes().decodeToString() }
            return BpmnModelFile(name = path.substringAfterLast('/').removeSuffix(".bpmn"), xml = xml)
        }
    }
}
