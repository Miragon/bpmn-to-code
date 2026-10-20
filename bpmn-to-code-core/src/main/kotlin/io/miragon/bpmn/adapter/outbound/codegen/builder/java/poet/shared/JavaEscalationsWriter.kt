package io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

internal object JavaEscalationsWriter : JavaSharedDefinitionWriter<Pair<String, String>>() {

    override val type = SharedDefinitionType.ESCALATIONS
    override val javadoc = "BPMN escalation definitions with name and code, as thrown and caught by the processes.\n"
    override val elementType: ClassName = JavaRuntimeTypes.BPMN_ESCALATION_DEFINITION

    override fun valueOf(entry: SharedDefinitionsGraph.Entry): Pair<String, String> = entry.value to checkNotNull(entry.code)

    override fun initializer(value: Pair<String, String>): CodeBlock = CodeBlock.of($$"new $T($S, $S)", elementType, value.first, value.second)
}
