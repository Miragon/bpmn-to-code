object ${typeName} {
  val PROCESS_ID: ${types.ProcessId} = ${types.ProcessId}(${processId})

  val PROCESS_ENGINE: ${types.BpmnEngine} = ${types.BpmnEngine}.${processEngine}
<#if flowNodes??>

  /**
   * Typed navigation over the process flow: one nested object per BPMN element.
   */
  object FlowNodes {
    val all: ${types.List}<${types.FlowNode}> = listOf(
<#list flowNodes as node>
      ${node.objectName},
</#list>
    )
<#list flowNodes as node>

    object ${node.objectName} : ${types.AbstractFlowNode}(
      id = ${types.ElementId}(${node.objectName}.ELEMENT_ID),
      elementType = ${types.BpmnElementType}.${node.elementType},
<#if node.name??>
      name = ${node.name},
</#if>
<#if node.interfaces?has_content>
    ), ${node.interfaces?join(", ")} {
<#else>
    ) {
</#if>
<#if node.eventType??>
      override val eventType: ${types.BpmnEventType} = ${types.BpmnEventType}.${node.eventType}

</#if>
      const val ELEMENT_ID: ${types.String} = ${node.elementId}
<#if node.jobType??>

      override val jobType: ${types.String} = ${node.jobType}
</#if>
<#if node.calledProcess??>

      override val calledProcess: ${types.ProcessId} = ${types.ProcessId}(${node.calledProcess})
</#if>
<#if node.timer??>

      override val timer: ${types.BpmnTimer} = ${types.BpmnTimer}(
        type = ${types.TimerType}.${node.timer.type},
        timerValue = ${node.timer.value},
      )
</#if>
<#if node.message??>
<@sharedName property="message" type=types.MessageName definition=node.message/>
</#if>
<#if node.signal??>
<@sharedName property="signal" type=types.SignalName definition=node.signal/>
</#if>
<#if node.error??>
<@sharedNameAndCode property="error" type=types.BpmnErrorDefinition definition=node.error/>
</#if>
<#if node.escalation??>
<@sharedNameAndCode property="escalation" type=types.BpmnEscalationDefinition definition=node.escalation/>
</#if>
<#if node.attachedTo??>

      override val attachedTo: ${node.attachedTo}
        get() = ${node.attachedTo}
</#if>
<#if node.interrupting??>

<#if node.attachedTo??>
      override val isInterrupting: ${types.Boolean} = ${node.interrupting?c}
<#else>
      val isInterrupting: ${types.Boolean} = ${node.interrupting?c}
</#if>
</#if>
<#if node.variables?has_content>

      override val variables: Variables = Variables
</#if>
<#if node.successors?has_content>

      override val next: Next = Next
</#if>
<#if node.interiorStarts?has_content>

      override val startEvents: Start = Start
</#if>
<#list node.variables>

      object Variables : ${types.RegisteredVariableDefinitions}() {
<#items as variable>
        val ${variable.name}: ${types.VariableName}.${variable.subtype} = ${variable.subtype?uncap_first}(${variable.nameConstant})
<#sep>

</#sep>
</#items>
      }
</#list>
<@mappings objectName="Inputs" mappings=node.inputs/>
<@mappings objectName="Outputs" mappings=node.outputs/>
<#list node.successors>

      object Next {
<#items as successor>
        val ${successor.name}: ${successor.type}<${successor.target}>
<#if successor.flows?size == 1>
          get() = ${types.SequenceFlows}.single(
<@flowArguments flow=successor.flows[0] idParameter="flowId" target=successor.target indent="            "/>
          )
<#elseif successor.flows?has_content>
          get() = ${types.SequenceFlows}(
            target = ${successor.target},
            flows = listOf(
<#list successor.flows as flow>
              ${types.SequenceFlow}(
<@flowArguments flow=flow idParameter="id" target=successor.target indent="                "/>
              ),
</#list>
            ),
          )
<#else>
          get() = ${successor.type}(target = ${successor.target})
</#if>
<#sep>

</#sep>
</#items>
      }
</#list>
<#list node.interiorStarts>

      object Start {
<#items as start>
        val ${start.name}: ${start.objectName}
          get() = ${start.objectName}
<#sep>

</#sep>
</#items>
      }
</#list>
    }
</#list>
  }
</#if>
}
<#macro sharedName property type definition>

<#if definition.reference??>
      override val ${property}: ${type} = ${definition.reference}
<#else>
      override val ${property}: ${type} = ${type}(${definition.literal})
</#if>
</#macro>
<#macro sharedNameAndCode property type definition>

<#if definition.reference??>
      override val ${property}: ${type} = ${definition.reference}
<#else>
      override val ${property}: ${type} = ${type}(
        name = ${definition.name},
        code = ${definition.code},
      )
</#if>
</#macro>
<#macro mappings objectName mappings>
<#list mappings>

      object ${objectName} {
<#items as mapping>
        val ${mapping.name}: ${types.InputOutputMapping} = ${types.InputOutputMapping}(
          target = ${mapping.target},
<#if mapping.source??>
          source = ${mapping.source},
</#if>
<#if mapping.sourceExpression??>
          sourceExpression = ${mapping.sourceExpression},
</#if>
        )
<#sep>

</#sep>
</#items>
      }
</#list>
</#macro>
<#macro flowArguments flow idParameter target indent>
${indent}${idParameter} = ${types.ElementId}(${flow.id}),
<#if flow.name??>
${indent}name = ${flow.name},
</#if>
<#if flow.conditionExpression??>
${indent}conditionExpression = ${flow.conditionExpression},
</#if>
<#if flow.defaultFlow>
${indent}isDefault = true,
</#if>
${indent}target = ${target},
</#macro>
