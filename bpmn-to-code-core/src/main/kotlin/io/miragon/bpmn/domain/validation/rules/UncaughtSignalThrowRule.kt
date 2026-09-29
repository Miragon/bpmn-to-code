package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.NamedEventUsage
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.EventDirection

/**
 * Flags a signal that is thrown (signal end / intermediate throw event) but never caught anywhere in
 * the loaded fileset — a broadcast that goes nowhere, which no single-model rule can detect since the
 * subscriber may live in another process file.
 *
 * Reported as WARN, not ERROR: signals are broadcast, so a legitimate subscriber outside the loaded
 * fileset is possible and the rule can only warn. Cross-model — only meaningful with the whole related
 * fileset loaded together, so it is opt-in (see BpmnRules).
 */
class UncaughtSignalThrowRule : UnmatchedEventUsageRule(flagged = EventDirection.THROW) {

    override val id = "uncaught-signal-throw"

    override fun usagesOf(model: ProcessModel): List<NamedEventUsage> = model.signalUsages()

    override fun message(usage: NamedEventUsage): String = "Signal '${usage.name}' is thrown by '${usage.node.id}' but has no catching event in the loaded models."
}
