package io.miragon.bpmn.adapter.outbound.codegen.freemarker

/**
 * A node's error or escalation, which the template writes as
 * - its shared constant [reference], e.g. `Errors.MIRAVELO_APPLICATION_INVALID`
 * - or, when no shared constant holds it, a definition of [name] and [code],
 *   e.g. `BpmnErrorDefinition("miravelo.applicationInvalid", "applicationInvalid")` in Kotlin
 */
internal data class SharedNameAndCode(val reference: String?, val name: String, val code: String)
