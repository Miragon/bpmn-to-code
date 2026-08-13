package io.miragon.bpmn.adapter.outbound.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import io.miragon.bpmn.adapter.outbound.engine.dialect.CamundaDialect
import io.miragon.bpmn.adapter.outbound.engine.dialect.EngineDialect
import io.miragon.bpmn.adapter.outbound.engine.dialect.ZeebeDialect
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.ProcessEngine

internal class ExtractBpmnAdapter(
    private val dialects: Map<ProcessEngine, EngineDialect> = ExtractBpmnAdapter.dialects,
) : ExtractBpmnPort {

    override fun extract(
        bpmnFile: BpmnResource,
        engine: ProcessEngine,
    ): ProcessModel {
        val dialect = dialects[engine] ?: error("No dialect found for engine: $engine")
        return try {
            logger.info { "Extracting model '${bpmnFile.fileName}' for '$engine'" }
            ProcessModelReader(dialect).read(bpmnFile.content)
        } catch (ex: IllegalStateException) {
            throw IllegalStateException(
                "Failed to extract file: ${bpmnFile.fileName}. Please check its a valid file for $engine",
                ex,
            )
        } catch (ex: IllegalArgumentException) {
            throw IllegalStateException(
                "Failed to extract file: ${bpmnFile.fileName}. Please check its a valid file for $engine",
                ex,
            )
        }
    }

    companion object {
        private const val CAMUNDA_7_NAMESPACE = "http://camunda.org/schema/1.0/bpmn"
        private const val OPERATON_NAMESPACE = "http://operaton.org/schema/1.0/bpmn"

        private val logger = KotlinLogging.logger {}

        /**
         * The engine registry (ADR 004). Reading a BPMN file is the same work for every engine apart from
         * its own namespace, so a new engine contributes a dialect here rather than its own reader.
         *
         * Camunda 7 and Operaton share the identical element and attribute vocabulary and differ only in
         * that namespace, so both use the same dialect with their own value (see ADR 010).
         */
        val dialects = mapOf(
            ProcessEngine.ZEEBE to ZeebeDialect(),
            ProcessEngine.CAMUNDA_7 to CamundaDialect(CAMUNDA_7_NAMESPACE),
            ProcessEngine.OPERATON to CamundaDialect(OPERATON_NAMESPACE),
        )
    }
}
