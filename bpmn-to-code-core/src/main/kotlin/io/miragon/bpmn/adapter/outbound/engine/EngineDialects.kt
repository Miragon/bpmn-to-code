package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.CamundaDialect
import io.miragon.bpmn.adapter.outbound.engine.dialect.EngineDialect
import io.miragon.bpmn.adapter.outbound.engine.dialect.ZeebeDialect
import io.miragon.bpmn.domain.shared.ProcessEngine

/**
 * The engine registry. Reading a BPMN file is the same work for every engine apart from its own namespace, so a new
 * engine contributes a dialect here rather than its own reader, and is detected by that dialect's namespace.
 *
 * Camunda 7 and Operaton share the identical element and attribute vocabulary and differ only in that namespace, so
 * both use the same dialect with their own value.
 */
internal object EngineDialects {

    private const val CAMUNDA_7_NAMESPACE = "http://camunda.org/schema/1.0/bpmn"
    private const val OPERATON_NAMESPACE = "http://operaton.org/schema/1.0/bpmn"

    /**
     * In the order engines are detected: a model declaring the namespaces of several engines targets the first.
     */
    val byEngine: Map<ProcessEngine, EngineDialect> = linkedMapOf(
        ProcessEngine.ZEEBE to ZeebeDialect(),
        ProcessEngine.OPERATON to CamundaDialect(OPERATON_NAMESPACE),
        ProcessEngine.CAMUNDA_7 to CamundaDialect(CAMUNDA_7_NAMESPACE),
    )

    /**
     * The engine a BPMN file targets, judged by the namespaces it declares; `null` for plain BPMN without engine
     * extensions. Best effort: it is used to warn when the selected engine does not match the model.
     */
    fun detect(content: String): ProcessEngine? = byEngine.entries.firstOrNull { (_, dialect) -> dialect.namespace in content }?.key
}
