package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnDefinitionsReader.extractVariantName
import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnDefinitionsReader.getProcessId
import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnDefinitionsReader.getProcessName
import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnDefinitionsReader.isExecutable
import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnDefinitionsReader.readRootElements
import io.miragon.bpmn.adapter.outbound.engine.bpmn.BpmnStructureReader
import io.miragon.bpmn.adapter.outbound.engine.dialect.EngineDialect
import io.miragon.bpmn.adapter.outbound.engine.xml.SecureBpmnParser
import io.miragon.bpmn.domain.ProcessModel

/**
 * Reads a [ProcessModel] from raw BPMN bytes.
 *
 * The read is split along the only axis that varies between engines. A BPMN file has two halves: the
 * `bpmn:Process` scope tree, walked by [BpmnStructureReader], and the surrounding `bpmn:Definitions`
 * metadata and root elements, read by `BpmnDefinitionsReader`. Both are pure BPMN and identical for every
 * engine; everything living in an engine's own namespace comes from the [EngineDialect]. Supporting
 * another engine therefore means passing a different dialect, not writing another reader (see ADR 004).
 */
internal class ProcessModelReader(private val dialect: EngineDialect) {

    fun read(bytes: ByteArray): ProcessModel {
        val modelInstance = SecureBpmnParser.readModelFromBytes(bytes)
        val (flowNodes, sequenceFlows) = BpmnStructureReader(modelInstance, dialect).read()
        return ProcessModel(
            flowNodes = flowNodes,
            sequenceFlows = sequenceFlows,
            processId = modelInstance.getProcessId(),
            processName = modelInstance.getProcessName(),
            definitions = modelInstance.readRootElements(dialect::correlationKeyOf),
            isExecutable = modelInstance.isExecutable(),
            detectedEngine = EngineDetector.detect(bytes.decodeToString()),
            variantName = modelInstance.extractVariantName(),
        )
    }
}
