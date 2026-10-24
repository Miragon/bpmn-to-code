package io.miragon.bpmn.adapter.outbound.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import io.miragon.bpmn.adapter.outbound.engine.dialect.EngineDialect
import io.miragon.bpmn.application.port.outbound.ExtractBpmnPort
import io.miragon.bpmn.domain.BpmnReadException
import io.miragon.bpmn.domain.BpmnResource
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.ProcessEngine

internal class ExtractBpmnAdapter(
    private val dialects: Map<ProcessEngine, EngineDialect> = EngineDialects.byEngine,
) : ExtractBpmnPort {

    override fun extract(bpmnFile: BpmnResource, engine: ProcessEngine): ProcessModel {
        val dialect = dialects[engine] ?: error("No dialect found for engine: $engine")
        return try {
            logger.info { "Extracting model '${bpmnFile.fileName}' for '$engine'" }
            ProcessModelReader(dialect).read(bpmnFile.content)
        } catch (ex: IllegalStateException) {
            throw unreadable(bpmnFile = bpmnFile, engine = engine, cause = ex)
        } catch (ex: IllegalArgumentException) {
            throw unreadable(bpmnFile = bpmnFile, engine = engine, cause = ex)
        } catch (ex: SecurityException) {
            throw unreadable(bpmnFile = bpmnFile, engine = engine, cause = ex)
        }
    }

    private fun unreadable(bpmnFile: BpmnResource, engine: ProcessEngine, cause: RuntimeException) = BpmnReadException(
        message = "Failed to extract file: ${bpmnFile.fileName}. Please check its a valid file for $engine",
        cause = cause,
    )

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
