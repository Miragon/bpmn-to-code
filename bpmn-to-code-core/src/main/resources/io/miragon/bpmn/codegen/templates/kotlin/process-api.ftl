<#import "macros.ftl" as kt>
object ${typeName} {
  val PROCESS_ID: ${t.ProcessId} = ${t.ProcessId}(${processId})

  val PROCESS_ENGINE: ${t.BpmnEngine} = ${t.BpmnEngine}.${processEngine}
<#if flowNodes??>

  /**
   * Typed navigation over the process flow: one nested object per BPMN element.
   */
  object FlowNodes {
    val all: ${t.List}<${t.FlowNode}> = listOf(
<#list flowNodes as node>
      ${node.objectName},
</#list>
    )
<#list flowNodes as node>

    object ${node.objectName} : ${t.AbstractFlowNode}(
<#list node.superArguments as argument>
      ${argument.name} = ${argument.value},
</#list>
    )<#if node.supertypes?has_content>, ${node.supertypes}</#if> {
<@kt.properties node.properties "      "/>
<#if node.variables?has_content>

      object Variables : ${t.RegisteredVariableDefinitions}() {
<@kt.properties node.variables "        "/>
      }
</#if>
<#if node.inputs?has_content>

      object Inputs {
<@kt.properties node.inputs "        "/>
      }
</#if>
<#if node.outputs?has_content>

      object Outputs {
<@kt.properties node.outputs "        "/>
      }
</#if>
<#if node.successors?has_content>

      object Next {
<@kt.properties node.successors "        "/>
      }
</#if>
<#if node.interiorStarts?has_content>

      object Start {
<@kt.properties node.interiorStarts "        "/>
      }
</#if>
    }
</#list>
  }
</#if>
}
