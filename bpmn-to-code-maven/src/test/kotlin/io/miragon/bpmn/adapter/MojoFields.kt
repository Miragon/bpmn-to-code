package io.miragon.bpmn.adapter

/**
 * Sets a mojo parameter the way Maven injects it: straight into its field, which a superclass may declare.
 */
internal fun setField(obj: Any, name: String, value: Any) {
    val hierarchy = generateSequence<Class<*>>(obj.javaClass) { it.superclass }
    val field = hierarchy.flatMap { it.declaredFields.asSequence() }.first { it.name == name }
    field.isAccessible = true
    field.set(obj, value)
}
