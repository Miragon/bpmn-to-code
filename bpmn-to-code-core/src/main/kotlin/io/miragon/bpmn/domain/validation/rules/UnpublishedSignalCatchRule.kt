package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.NamedEventUsage
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.EventDirection

/**
 * Flags a signal that is caught (signal start / intermediate catch / boundary event) but never thrown
 * anywhere in the loaded fileset — an orphaned subscriber waiting for a broadcast that no process
 * publishes. The mirror of [UncaughtSignalThrowRule]; no single-model rule can detect it since the
 * thrower may live in another process file.
 *
 * Reported as WARN, not ERROR: signals are broadcast, so a legitimate publisher outside the loaded
 * fileset is possible and the rule can only warn. Cross-model — only meaningful with the whole related
 * fileset loaded together, so it is opt-in (see BpmnRules).
 */
class UnpublishedSignalCatchRule : UnmatchedEventUsageRule(flagged = EventDirection.CATCH) {

    override val id = "unpublished-signal-catch"

    override fun usagesOf(model: ProcessModel): List<NamedEventUsage> = model.signalUsages()

    override fun message(usage: NamedEventUsage): String = "Signal '${usage.name}' is caught by '${usage.node.id}' but has no throwing event in the loaded models."
}
