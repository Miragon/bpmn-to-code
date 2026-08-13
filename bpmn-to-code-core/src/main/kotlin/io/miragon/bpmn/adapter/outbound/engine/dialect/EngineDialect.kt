package io.miragon.bpmn.adapter.outbound.engine.dialect

import io.miragon.bpmn.domain.shared.CallActivityDefinition
import io.miragon.bpmn.domain.shared.IoMapping
import io.miragon.bpmn.domain.shared.MultiInstanceDefinition
import io.miragon.bpmn.domain.shared.TaskImplementation
import io.miragon.bpmn.domain.shared.VariableDefinition
import org.camunda.bpm.model.bpmn.instance.CallActivity
import org.camunda.bpm.model.bpmn.instance.FlowNode
import org.camunda.bpm.model.bpmn.instance.Message
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics

/**
 * The engine-specific half of BPMN extraction.
 *
 * `BpmnStructureReader` walks the standard BPMN structure — containment, sequence flows, event
 * definitions, boundary attachments — which is identical for every engine. Everything that lives in an
 * engine's own namespace (`zeebe:*`, `camunda:*`, `operaton:*`) is normalised here, so a new engine only
 * has to implement this interface. See ADR 004 and ADR 017.
 */
internal interface EngineDialect {

    /**
     * The engine's own XML namespace.
     */
    val namespace: String

    /**
     * Names of the `bpmn:extensionElements` children in [namespace] that this dialect reads **in full**.
     *
     * These are left out of a node's raw `extensions`, which exists to carry what is *not* normalised
     * (ADR 018, layer 3) — emitting both would state the same fact twice and let the two drift apart.
     *
     * Membership is a claim about coverage, so an element belongs here only if every attribute and child
     * it can carry ends up in a typed field. Partially read elements stay out and keep being reported raw.
     */
    val fullyReadExtensions: Set<String>

    /**
     * Names of the foreign-namespace *attributes* on [node] that this dialect read into a typed field.
     * These are left out of the node's `engineAttributes` for the same reason as [fullyReadExtensions].
     *
     * Resolved per node rather than declared as a fixed set: an engine may offer several mutually
     * exclusive attributes for one concept, and only the one that actually won is normalised. The others
     * stay in the raw layer, which is where a model that declares two of them remains readable.
     */
    fun fullyReadAttributesOf(node: FlowNode): Set<String>

    /**
     * The service-task-like implementation of [node], or `null` if the node has no such concept.
     */
    fun implementationOf(node: FlowNode): TaskImplementation?

    /**
     * Input/output parameter mapping of [node] (`zeebe:ioMapping` / `camunda:inputOutput`).
     */
    fun ioMappingOf(node: FlowNode): IoMapping?

    /**
     * Engine-specific collection/element bindings on top of the standard loop characteristics.
     */
    fun multiInstanceBindingsOf(
        loop: MultiInstanceLoopCharacteristics,
        base: MultiInstanceDefinition
    ): MultiInstanceDefinition

    /**
     * Variables declared by [node], each tagged with its direction (see ADR 015).
     */
    fun variablesOf(node: FlowNode): List<VariableDefinition>

    /**
     * The called-process binding of [callActivity], including the variables propagated in and out.
     */
    fun callActivityOf(callActivity: CallActivity): CallActivityDefinition

    /**
     * The correlation-key expression declared on [message], where the engine supports one.
     */
    fun correlationKeyOf(message: Message): String? = null
}
