<#include "file-header.ftl">

public static class ${typeName}
{
    public const string ProcessId = ${processId};

    public const Runtime.BpmnEngine ProcessEngine = Runtime.BpmnEngine.${processEngine};

    /// <summary>
    /// The runtime types this API's flow nodes are built from; inlined so the file needs no package.
    /// </summary>
    public static class Runtime
    {
<#include "runtime-types.cs" parse=false>
    }
<#if flowNodes??>

    /// <summary>
    /// Typed navigation over the process flow: one nested singleton class per BPMN element, reached as FlowNodes.Element.Instance.
    /// </summary>
    public static class FlowNodes
    {
<#list flowNodes as node>
        public sealed class ${node.className} : ${node.interfaces?join(", ")}
        {
            public static readonly ${node.className} Instance = new();
            private ${node.className}() { }

            public const string ElementId = ${node.elementId};
            public Runtime.ElementId Id { get; } = new(ElementId);
            public Runtime.BpmnElementType ElementType => Runtime.BpmnElementType.${node.elementType};
<#if node.eventType??>
            public Runtime.BpmnEventType EventType => Runtime.BpmnEventType.${node.eventType};
</#if>
            public string? Name => ${node.name};
<#if node.jobType??>
            public string ${memberName("JobType", node.className)} { get; } = ${node.jobType};
</#if>
<#if node.calledProcess??>
            public Runtime.ProcessId ${memberName("CalledProcess", node.className)} { get; } = new(${node.calledProcess});
</#if>
<#if node.timer??>
            public Runtime.BpmnTimer ${memberName("Timer", node.className)} { get; } = new(Runtime.TimerType.${node.timer.type}, ${node.timer.expression});
</#if>
<#if node.message??>
            public Runtime.MessageName ${memberName("Message", node.className)} { get; } = new(${node.message});
</#if>
<#if node.signal??>
            public Runtime.SignalName ${memberName("Signal", node.className)} { get; } = new(${node.signal});
</#if>
<#if node.error??>
            public Runtime.BpmnErrorDefinition ${memberName("Error", node.className)} { get; } = new(${node.error.name}, ${node.error.code});
</#if>
<#if node.escalation??>
            public Runtime.BpmnEscalationDefinition ${memberName("Escalation", node.className)} { get; } = new(${node.escalation.name}, ${node.escalation.code});
</#if>
<#if node.attachedTo??>
            public ${node.attachedTo} ${memberName("AttachedTo", node.className)} => ${node.attachedTo}.Instance;
</#if>
<#if node.interrupting??>
            public bool ${memberName("IsInterrupting", node.className)} => ${node.interrupting?c};
</#if>
<#list node.variables>

            public NodeVariables Variables { get; } = new();
            Runtime.VariableDefinitions Runtime.IHasVariables.Variables => Variables;
            public sealed class NodeVariables : Runtime.VariableDefinitions
            {
<#items as variable>
                public Runtime.VariableName.${variable.subtype} ${memberName(variable.name, "NodeVariables")} { get; } = new(${variable.nameConstant});
</#items>
                public override System.Collections.Generic.IReadOnlyList<Runtime.VariableName> All => new Runtime.VariableName[] { ${node.variables?map(variable -> memberName(variable.name, "NodeVariables"))?join(", ")} };
            }
</#list>
<@mappings property="Inputs" className="InputMappings" mappings=node.inputs/>
<@mappings property="Outputs" className="OutputMappings" mappings=node.outputs/>
<#list node.successors>

            public Successors Next => new();
            public sealed class Successors
            {
<#items as successor>
<#assign declaration = "public Runtime.${successor.type}<${successor.target}> ${memberName(successor.target, 'Successors')}">
<#assign target = "FlowNodes.${successor.target}.Instance">
<#if successor.flows?size == 1>
                ${declaration} => Runtime.SequenceFlows.Single(${singleFlowArguments(successor.flows[0], target)});
<#elseif successor.flows?has_content>
                ${declaration} => new(${target}, new Runtime.SequenceFlow<${successor.target}>[] { ${sequenceFlows(successor.flows, target)} });
<#else>
                ${declaration} => new(${target});
</#if>
</#items>
            }
</#list>
<#list node.interiorStarts>

            public Interior Start => new();
            public sealed class Interior
            {
<#items as start>
                public ${start} ${memberName(start, "Interior")} => ${start}.Instance;
</#items>
            }
</#list>
        }
<#sep>

</#sep>
</#list>

        public static System.Collections.Generic.IReadOnlyList<Runtime.IFlowNode> All => new Runtime.IFlowNode[]
        {
<#list flowNodes as node>
            ${node.className}.Instance,
</#list>
        };
    }
</#if>
}
<#macro mappings property className mappings>
<#list mappings>

            public ${className} ${property} { get; } = new();
            public sealed class ${className}
            {
<#items as mapping>
                public Runtime.InputOutputMapping ${memberName(mapping.name, className)} { get; } = new(${mapping.target}, ${mapping.source}, ${mapping.sourceExpression});
</#items>
            }
</#list>
</#macro>
<#-- C# rejects a member named like its enclosing type (CS0542): a node class `Timer` declares its timer as `Timer_`. -->
<#function memberName name enclosingType>
  <#if name == enclosingType>
    <#return name + "_">
  </#if>
  <#return name>
</#function>
<#-- e.g. `new("flow_isSolventToConcludeContract"), FlowNodes.SubProcessConcludeContract.Instance, name: "Yes", isDefault: true` -->
<#function singleFlowArguments flow target>
  <#local arguments = ["new(${flow.id})", target]>
  <#if flow.name??>
    <#local arguments += ["name: ${flow.name}"]>
  </#if>
  <#if flow.conditionExpression??>
    <#local arguments += ["conditionExpression: ${flow.conditionExpression}"]>
  </#if>
  <#if flow.defaultFlow>
    <#local arguments += ["isDefault: true"]>
  </#if>
  <#return arguments?join(", ")>
</#function>
<#-- e.g. `new(new("flow_vip"), null, "=customer.isVip", false, FlowNodes.Approve.Instance)`, one per flow -->
<#function sequenceFlows flows target>
  <#local constructions = flows?map(flow -> "new(new(${flow.id}), ${flow.name!'null'}, ${flow.conditionExpression!'null'}, ${flow.defaultFlow?c}, ${target})")>
  <#return constructions?join(", ")>
</#function>
