package io.miragon.bpmn.adapter.outbound.codegen

import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

/**
 * Renders the file of one kind of shared definition in one output language.
 */
internal fun interface SharedDefinitionsBuilder {

    fun render(group: SharedDefinitionsGraph.Group, packagePath: PackagePath): SourceCode
}
