<#include "file-header.ftl">

public static class ${typeName}
{
    public const string ProcessId = ${processId};

    public const ${runtime}.BpmnEngine ProcessEngine = ${runtime}.BpmnEngine.${processEngine};
<#if flowNodes??>

    /// <summary>
    /// Typed navigation over the process flow: one property per BPMN element, typed by a nested singleton class.
    /// </summary>
    public static class FlowNodes
    {
<#list flowNodes as node>
        public static ${node.type} ${node.property} => ${node.type}.Instance;

        public sealed class ${node.type} : ${node.interfaces?join(", ")}
        {
            internal static readonly ${node.type} Instance = new();
            private ${node.type}() { }

            public const string ElementId = ${node.elementId};
            public ${runtime}.ElementId Id { get; } = new(ElementId);
            public ${runtime}.BpmnElementType ElementType => ${runtime}.BpmnElementType.${node.elementType};
<#if node.eventType??>
            public ${runtime}.BpmnEventType EventType => ${runtime}.BpmnEventType.${node.eventType};
</#if>
            public string? Name => ${node.name};
<#if node.jobType??>
            public string JobType { get; } = ${node.jobType};
</#if>
<#if node.calledProcess??>
            public ${runtime}.ProcessId CalledProcess { get; } = new(${node.calledProcess});
</#if>
<#if node.timer??>
            public ${runtime}.BpmnTimer Timer { get; } = new(${runtime}.TimerType.${node.timer.type}, ${node.timer.expression});
</#if>
<#if node.message??>
            public ${runtime}.MessageName Message { get; } = new(${node.message});
</#if>
<#if node.signal??>
            public ${runtime}.SignalName Signal { get; } = new(${node.signal});
</#if>
<#if node.error??>
            public ${runtime}.BpmnErrorDefinition Error { get; } = new(${node.error.name}, ${node.error.code});
</#if>
<#if node.escalation??>
            public ${runtime}.BpmnEscalationDefinition Escalation { get; } = new(${node.escalation.name}, ${node.escalation.code});
</#if>
<#if node.attachedTo??>
            public ${node.attachedTo.type} AttachedTo => FlowNodes.${node.attachedTo.property};
</#if>
<#if node.interrupting??>
            public bool IsInterrupting => ${node.interrupting?c};
</#if>
<#list node.variables>

            public NodeVariables Variables { get; } = new();
            ${runtime}.VariableDefinitions ${runtime}.IHasVariables.Variables => Variables;
            public sealed class NodeVariables : ${runtime}.VariableDefinitions
            {
<#items as variable>
                public ${runtime}.VariableName.${variable.subtype} ${memberName(variable.name, "NodeVariables")} { get; } = new(${variable.nameConstant});
</#items>
                public override global::System.Collections.Generic.IReadOnlyList<${runtime}.VariableName> All => new ${runtime}.VariableName[] { ${node.variables?map(variable -> memberName(variable.name, "NodeVariables"))?join(", ")} };
            }
</#list>
<@mappings property="Inputs" className="InputMappings" mappings=node.inputs/>
<@mappings property="Outputs" className="OutputMappings" mappings=node.outputs/>
<#list node.successors>

            public Successors Next { get; } = new();
            public sealed class Successors
            {
<#items as successor>
<#assign member = memberName(successor.target.property, "Successors")>
<#assign successorType = "${runtime}.${successor.type}<${successor.target.type}>">
<#assign target = "FlowNodes.${successor.target.property}">
<#if successor.flows?size == 1>
                public ${successorType} ${member} => _${member?uncap_first} ??= ${runtime}.SequenceFlows.Single(${singleFlowArguments(successor.flows[0], target)});
<#elseif successor.flows?has_content>
                public ${successorType} ${member} => _${member?uncap_first} ??= new(${target}, new ${runtime}.SequenceFlow<${successor.target.type}>[] { ${sequenceFlows(successor.flows, target)} });
<#else>
                public ${successorType} ${member} => _${member?uncap_first} ??= new(${target});
</#if>
                private ${successorType}? _${member?uncap_first};
</#items>
            }
</#list>
<#list node.interiorStarts>

            public Interior Start { get; } = new();
            public sealed class Interior
            {
<#items as start>
                public ${start.type} ${memberName(start.property, "Interior")} => FlowNodes.${start.property};
</#items>
            }
</#list>
        }
<#sep>

</#sep>
</#list>

        public static global::System.Collections.Generic.IReadOnlyList<${runtime}.IFlowNode> All => new ${runtime}.IFlowNode[]
        {
<#list flowNodes as node>
            ${node.property},
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
                public ${runtime}.InputOutputMapping ${memberName(mapping.name, className)} { get; } = new(${mapping.target}, ${mapping.source}, ${mapping.sourceExpression});
</#items>
            }
</#list>
</#macro>
<#-- C# rejects a member named like its enclosing type (CS0542): a successor `Successors` is declared as `Successors_`. -->
<#function memberName name enclosingType>
  <#if name == enclosingType>
    <#return name + "_">
  </#if>
  <#return name>
</#function>
<#-- e.g. `new("flow_isSolventToConcludeContract"), FlowNodes.SubProcessConcludeContract, name: "Yes", isDefault: true` -->
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
<#-- e.g. `new(new("flow_vip"), null, "=customer.isVip", false, FlowNodes.Approve)`, one per flow -->
<#function sequenceFlows flows target>
  <#local constructions = flows?map(flow -> "new(new(${flow.id}), ${flow.name!'null'}, ${flow.conditionExpression!'null'}, ${flow.defaultFlow?c}, ${target})")>
  <#return constructions?join(", ")>
</#function>
