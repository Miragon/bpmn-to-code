package io.miragon.bpmn.adapter.outbound.codegen.flownode

/**
 * A node's message or signal name, which the template writes as
 * - its shared constant [reference], e.g. `Messages.MIRAVELO_CONTRACT_SIGNED`
 * - or, when no shared constant holds it, the [literal] wrapped in its runtime type,
 *   e.g. `MessageName("miravelo.contractSigned")`
 */
internal data class SharedName(val reference: String?, val literal: String)
