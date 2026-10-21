<#list flowNodes! as node>
export class ${node.className} implements ${node.interfaces?join(", ")} {
  static readonly INSTANCE = new ${node.className}();

  private constructor() {}

  readonly ELEMENT_ID = ${node.elementId};
  readonly id = this.ELEMENT_ID;
  readonly elementType = ${node.elementType};
<#if node.eventType??>
  readonly eventType = ${node.eventType};
</#if>
  readonly name = ${node.name};
<#if node.jobType??>
  readonly jobType = ${node.jobType};
</#if>
<#if node.calledProcess??>
  readonly calledProcess = ${node.calledProcess};
</#if>
<#if node.timer??>
  readonly timer = { type: ${node.timer.type}, timerValue: ${node.timer.value} } as const;
</#if>
<#if node.message??>
  readonly message = ${node.message};
</#if>
<#if node.signal??>
  readonly signal = ${node.signal};
</#if>
<#if node.error??>
<@sharedNameAndCode property="error" definition=node.error/>
</#if>
<#if node.escalation??>
<@sharedNameAndCode property="escalation" definition=node.escalation/>
</#if>
<#if node.interrupting??>
  readonly isInterrupting = ${node.interrupting?c};
</#if>
<#if node.throwsCompensation>
  readonly throwsCompensation = true;
</#if>
<#if node.attachedTo??>

  get attachedTo(): ${node.attachedTo} {
    return ${node.attachedTo}.INSTANCE;
  }
</#if>
<#list node.variables>

  readonly variables = ${runtimeValues.variableDefinitions}({
<#items as variable>
    ${variable.key}: ${runtimeValues[variable.subtype?uncap_first]}(${variable.nameConstant}),
</#items>
  });
</#list>
<@mappings property="Inputs" mappings=node.inputs/>
<@mappings property="Outputs" mappings=node.outputs/>
<#list node.successors>

  readonly next = {
<#items as successor>
    get ${successor.key}(): ${runtimeTypes[successor.type]}<${successor.target}> {
      return ${runtimeValues[successor.type?uncap_first]}(${successorArguments(successor)});
    },
</#items>
  };
</#list>
<#list node.interiorStarts>

  readonly startEvents = {
<#items as start>
    get ${start.key}(): ${start.className} {
      return ${start.className}.INSTANCE;
    },
</#items>
  };
</#list>
}

</#list>
export const ${typeName} = {
  PROCESS_ID: ${processId},
  PROCESS_ENGINE: ${processEngine},
<#if flowNodes??>
  FlowNodes: {
<#list flowNodes as node>
    ${node.className}: ${node.className}.INSTANCE,
</#list>
    get all(): readonly ${runtimeTypes.FlowNode}[] {
      return [
<#list flowNodes as node>
        ${node.className}.INSTANCE,
</#list>
      ];
    },
  },
</#if>
} as const;
<#macro sharedNameAndCode property definition>
<#if definition.reference??>
  readonly ${property} = ${definition.reference};
<#else>
  readonly ${property} = { name: ${definition.name}, code: ${definition.code} } as const;
</#if>
</#macro>
<#macro mappings property mappings>
<#list mappings>

  readonly ${property} = {
<#items as mapping>
    ${mapping.key}: { target: ${mapping.target}, source: ${mapping.source}, sourceExpression: ${mapping.sourceExpression} },
</#items>
  } as const;
</#list>
</#macro>
<#-- e.g. `SubProcessConcludeContract.INSTANCE, { id: "flow_isSolventToConcludeContract", name: "Yes", isDefault: true }` -->
<#function successorArguments successor>
  <#local arguments = ["${successor.target}.INSTANCE"]>
  <#list successor.flows as flow>
    <#local arguments += [flowLiteral(flow)]>
  </#list>
  <#return arguments?join(", ")>
</#function>
<#-- e.g. `{ id: "flow_isSolventToConcludeContract", name: "Yes", isDefault: true }` -->
<#function flowLiteral flow>
  <#local fields = ["id: ${flow.id}"]>
  <#if flow.name??>
    <#local fields += ["name: ${flow.name}"]>
  </#if>
  <#if flow.conditionExpression??>
    <#local fields += ["conditionExpression: ${flow.conditionExpression}"]>
  </#if>
  <#if flow.defaultFlow>
    <#local fields += ["isDefault: true"]>
  </#if>
  <#return "{ " + fields?join(", ") + " }">
</#function>
