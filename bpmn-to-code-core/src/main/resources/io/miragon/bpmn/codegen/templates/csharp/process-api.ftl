<#function member property>
<#if property.expressionBodied>
<#return "public ${property.type} ${property.name} => ${property.value};">
</#if>
<#return "public ${property.type} ${property.name} { get; } = ${property.value};">
</#function>
<#include "header.ftl">
public static class ${typeName}
{
    public const string ProcessId = ${processId};

    public const ${runtime}.BpmnEngine ProcessEngine = ${processEngine};
<#if flowNodes??>

    /// <summary>
    /// Typed navigation over the process flow: one nested singleton class per BPMN element, reached as FlowNodes.Element.Instance.
    /// </summary>
    public static class FlowNodes
    {
<#list flowNodes as node>
<#if node?index gt 0>

</#if>
        public sealed class ${node.className} : ${node.interfaces}
        {
            public static readonly ${node.className} Instance = new();
            private ${node.className}() { }

            public const string ElementId = ${node.elementId};
            public ${runtime}.ElementId Id { get; } = new(ElementId);
            public ${runtime}.BpmnElementType ElementType => ${node.elementType};
<#if node.eventType??>
            public ${runtime}.BpmnEventType EventType => ${node.eventType};
</#if>
            public string? Name => ${node.name};
<#list node.properties as property>
            ${member(property)}
</#list>
<#if node.variables??>

            public NodeVariables Variables { get; } = new();
            ${runtime}.VariableDefinitions ${runtime}.IHasVariables.Variables => Variables;
            public sealed class NodeVariables : ${runtime}.VariableDefinitions
            {
<#list node.variables.members as variable>
                ${member(variable)}
</#list>
                public override System.Collections.Generic.IReadOnlyList<${runtime}.VariableName> All => new ${runtime}.VariableName[] { ${node.variables.all} };
            }
</#if>
<#if node.inputs?has_content>

            public InputMappings Inputs { get; } = new();
            public sealed class InputMappings
            {
<#list node.inputs as mapping>
                ${member(mapping)}
</#list>
            }
</#if>
<#if node.outputs?has_content>

            public OutputMappings Outputs { get; } = new();
            public sealed class OutputMappings
            {
<#list node.outputs as mapping>
                ${member(mapping)}
</#list>
            }
</#if>
<#if node.successors?has_content>

            public Successors Next => new();
            public sealed class Successors
            {
<#list node.successors as successor>
                ${member(successor)}
</#list>
            }
</#if>
<#if node.interiorStarts?has_content>

            public Interior Start => new();
            public sealed class Interior
            {
<#list node.interiorStarts as start>
                ${member(start)}
</#list>
            }
</#if>
        }
</#list>

        public static System.Collections.Generic.IReadOnlyList<${runtime}.IFlowNode> All => new ${runtime}.IFlowNode[]
        {
<#list flowNodes as node>
            ${node.className}.Instance,
</#list>
        };
    }
</#if>
}
