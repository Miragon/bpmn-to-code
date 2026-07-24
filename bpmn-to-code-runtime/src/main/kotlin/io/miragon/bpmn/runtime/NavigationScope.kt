package io.miragon.bpmn.runtime

/**
 * A navigation scope whose [then] yields the scope's start element(s) — the process root or a subprocess
 * interior. Unlike [Navigable], a scope is **not** a [FlowNode]: it has no `id`/`elementType`, it is only an
 * entry point. A subprocess interior is exposed as a scope via [HasInner.inner].
 */
interface NavigationScope<out NEXT> {
    fun then(): NEXT
}
