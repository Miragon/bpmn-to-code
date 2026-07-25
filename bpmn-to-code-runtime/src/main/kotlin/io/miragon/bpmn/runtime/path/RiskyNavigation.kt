package io.miragon.bpmn.runtime.path

/**
 * Opt-in gate for the unchecked [jumpTo] escape hatch, so it stands out in code and review.
 */
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    message = "Unchecked navigation jump — bypasses the model adjacency check. " +
        "Prefer then / onto / enter / inside where possible.",
)
annotation class RiskyNavigation
