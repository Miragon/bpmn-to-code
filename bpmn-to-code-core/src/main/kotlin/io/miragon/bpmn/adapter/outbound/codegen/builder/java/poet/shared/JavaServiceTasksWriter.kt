package io.miragon.bpmn.adapter.outbound.codegen.builder.java.poet.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.definitions.SharedDefinitionsGraph

internal object JavaServiceTasksWriter : JavaSharedDefinitionWriter<String>() {

    override val type = SharedDefinitionType.SERVICE_TASKS
    override val javadoc = "Job worker task types used in {@code @JobWorker(type = ServiceTasks.X)} annotations.\n" +
        "Kept as {@code public static final String} because annotation arguments must be compile-time constants.\n"
    override val elementType: ClassName = ClassName.get(String::class.java)

    override fun valueOf(entry: SharedDefinitionsGraph.Entry): String = entry.value

    override fun initializer(value: String): CodeBlock = CodeBlock.of($$"$S", value)
}
