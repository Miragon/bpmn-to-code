using System.Collections.Generic;
using System.Linq;

namespace Miragon.BpmnToCode.Runtime;

/// <summary>Identifier of an element (task, gateway, event, ...) inside a BPMN process.</summary>
public sealed record ElementId(string Value)
{
    public override string ToString() => Value;
}

/// <summary>Identifier of a BPMN process definition.</summary>
public sealed record ProcessId(string Value)
{
    public override string ToString() => Value;
}

/// <summary>Name of a BPMN message used for correlation with message catch events.</summary>
public sealed record MessageName(string Value)
{
    public override string ToString() => Value;
}

/// <summary>Name of a BPMN signal broadcast to all matching catch events.</summary>
public sealed record SignalName(string Value)
{
    public override string ToString() => Value;
}

/// <summary>A process variable name whose subtype encodes the direction the declaring element uses it in.</summary>
public abstract record VariableName(string Value)
{
    public sealed override string ToString() => Value;

    /// <summary>A variable read by the declaring element.</summary>
    public sealed record Input(string Value) : VariableName(Value);

    /// <summary>A variable written by the declaring element.</summary>
    public sealed record Output(string Value) : VariableName(Value);

    /// <summary>A variable read and written by the declaring element.</summary>
    public sealed record InOut(string Value) : VariableName(Value);
}

/// <summary>The variables a node declares: <see cref="All"/> of them, those it reads (<see cref="Inputs"/>) and those it writes (<see cref="Outputs"/>).</summary>
public abstract class VariableDefinitions
{
    public abstract IReadOnlyList<VariableName> All { get; }

    public IReadOnlyList<VariableName> Inputs => All.Where(variable => variable is not VariableName.Output).ToArray();

    public IReadOnlyList<VariableName> Outputs => All.Where(variable => variable is not VariableName.Input).ToArray();
}

/// <summary>A BPMN timer definition: its type and its ISO 8601 date, duration or cycle.</summary>
public sealed record BpmnTimer(TimerType Type, string TimerValue);

/// <summary>A BPMN error definition: the name declared in the model and the code catch events match on.</summary>
public sealed record BpmnErrorDefinition(string Name, string Code);

/// <summary>A BPMN escalation definition: the name declared in the model and the code catch events match on.</summary>
public sealed record BpmnEscalationDefinition(string Name, string Code);

/// <summary>A variable mapping into or out of a called process; <see cref="Source"/> and <see cref="SourceExpression"/> are mutually exclusive.</summary>
public sealed record InputOutputMapping(string Target, string? Source = null, string? SourceExpression = null)
{
    public override string ToString() => Target;
}
