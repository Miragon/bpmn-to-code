package io.miragon.bpmn.adapter.outbound.codegen.builder.java.shared

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import io.miragon.bpmn.adapter.outbound.codegen.NameAndCodeConstants
import io.miragon.bpmn.adapter.outbound.codegen.SharedDefinitionType
import io.miragon.bpmn.adapter.outbound.codegen.builder.java.JavaNamesHolder
import io.miragon.bpmn.domain.SharedDefinitions
import io.miragon.bpmn.domain.shared.VariableMapping

internal object JavaEscalationsWriter : JavaSharedDefinitionWriter<Pair<String, String>>() {

    override val type = SharedDefinitionType.ESCALATIONS
    override val javadoc = "BPMN escalation definitions with name and code, as thrown and caught by the processes.\n"
    override val elementType: ClassName = ClassName.get(RUNTIME_PACKAGE, "BpmnEscalationDefinition")

    override fun definitionsOf(definitions: SharedDefinitions): List<VariableMapping<Pair<String, String>>> = definitions.escalations

    override fun initializer(definition: VariableMapping<Pair<String, String>>): CodeBlock {
        val constants = NameAndCodeConstants(definition)
        return CodeBlock.of(
            $$"new $T($N.$N, $N.$N)",
            elementType,
            JavaNamesHolder.NAME,
            constants.nameConstant,
            JavaNamesHolder.NAME,
            constants.codeConstant,
        )
    }

    override fun rawNames(definition: VariableMapping<Pair<String, String>>): List<Pair<String, String>> = NameAndCodeConstants(definition).rawValues()
}
