package io.miragon.bpmn.application.port.outbound

import io.miragon.bpmn.domain.GeneratedApiFile
import io.miragon.bpmn.domain.ProcessApiRequest

interface GenerateApiCodePort {
    fun generate(request: ProcessApiRequest): List<GeneratedApiFile>
}
