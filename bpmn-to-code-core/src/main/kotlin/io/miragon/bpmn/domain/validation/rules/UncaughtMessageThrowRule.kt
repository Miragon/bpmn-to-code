package io.miragon.bpmn.domain.validation.rules

import io.miragon.bpmn.domain.NamedEventUsage
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.EventDirection

/**
 * Flags a message that is thrown (message end / intermediate throw event, send task) but never caught
 * anywhere in the loaded fileset — silently lost cross-process communication that no single-model rule can
 * detect, since the catcher may live in another process file.
 *
 * Reported as WARN, not ERROR: a legitimate consumer outside the loaded fileset is possible, so the
 * rule can only warn. Cross-model — only meaningful with the whole related fileset loaded together,
 * so it is opt-in (see BpmnRules).
 */
class UncaughtMessageThrowRule : UnmatchedEventUsageRule(flagged = EventDirection.THROW) {

    override val id = "uncaught-message-throw"

    override fun usagesOf(model: ProcessModel): List<NamedEventUsage> = model.messageUsages()

    override fun message(usage: NamedEventUsage): String = "Message '${usage.name}' is thrown by '${usage.node.id}' but has no catching event in the loaded models."
}
