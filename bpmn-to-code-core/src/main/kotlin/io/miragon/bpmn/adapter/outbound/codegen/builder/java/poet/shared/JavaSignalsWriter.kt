package io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaRuntimeTypes
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

internal object JavaSignalsWriter : JavaSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.SIGNALS
    override val javadoc = "BPMN signal names broadcast and caught by signal events.\n"
    override val elementType: ClassName = JavaRuntimeTypes.SIGNAL_NAME

    override fun valueOf(entry: SharedDefinitionsGraph.Entry): String = entry.value

    override fun initializer(value: String): CodeBlock = CodeBlock.of($$"new $T($S)", elementType, value)
}
