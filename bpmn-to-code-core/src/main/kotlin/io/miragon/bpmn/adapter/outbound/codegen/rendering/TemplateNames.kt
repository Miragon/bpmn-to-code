package io.miragon.bpmn.adapter.outbound.codegen.rendering

import freemarker.template.SimpleScalar
import freemarker.template.TemplateHashModel
import freemarker.template.TemplateModel

/**
 * Names a template reads as a hash, e.g. `${types.SequenceFlows}`. Each lookup calls [nameOf], so the caller records
 * every name the template writes.
 */
internal class TemplateNames(private val nameOf: (String) -> String) : TemplateHashModel {

    override fun get(key: String): TemplateModel = SimpleScalar(nameOf(key))

    override fun isEmpty(): Boolean = false
}
