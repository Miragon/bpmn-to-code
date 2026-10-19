namespace Miragon.BpmnToCode.Runtime;

public sealed record ElementId(string Value)
{
    public override string ToString() => Value;

    public static implicit operator string(ElementId value) => value.Value;
}

public sealed record ProcessId(string Value)
{
    public override string ToString() => Value;

    public static implicit operator string(ProcessId value) => value.Value;
}

public sealed record MessageName(string Value)
{
    public override string ToString() => Value;

    public static implicit operator string(MessageName value) => value.Value;
}

public sealed record SignalName(string Value)
{
    public override string ToString() => Value;

    public static implicit operator string(SignalName value) => value.Value;
}

/// <summary>A process variable name whose subtype encodes the direction the declaring element uses it in.</summary>
public abstract record VariableName(string Value)
{
    public sealed override string ToString() => Value;

    public static implicit operator string(VariableName value) => value.Value;

    public sealed record Input(string Value) : VariableName(Value);

    public sealed record Output(string Value) : VariableName(Value);

    public sealed record InOut(string Value) : VariableName(Value);
}

/// <summary>The variables a node declares: All of them, those it reads (Inputs) and those it writes (Outputs).</summary>
public abstract class VariableDefinitions
{
    public abstract System.Collections.Generic.IReadOnlyList<VariableName> All { get; }

    public System.Collections.Generic.IReadOnlyList<VariableName> Inputs => System.Linq.Enumerable.ToArray(System.Linq.Enumerable.Where(All, variable => variable is not VariableName.Output));

    public System.Collections.Generic.IReadOnlyList<VariableName> Outputs => System.Linq.Enumerable.ToArray(System.Linq.Enumerable.Where(All, variable => variable is not VariableName.Input));
}

/// <summary>BPMN element type of a flow node; an event's definition is its EventType.</summary>
public enum BpmnElementType
{
    ServiceTask,
    UserTask,
    ReceiveTask,
    SendTask,
    ScriptTask,
    ManualTask,
    BusinessRuleTask,
    Task,
    ExclusiveGateway,
    ParallelGateway,
    InclusiveGateway,
    EventBasedGateway,
    ComplexGateway,
    SubProcess,
    EventSubProcess,
    Transaction,
    CallActivity,
    StartEvent,
    EndEvent,
    IntermediateCatchEvent,
    IntermediateThrowEvent,
    BoundaryEvent,
    Unknown,
}

/// <summary>Event definition of an event: None without one, Multiple with several.</summary>
public enum BpmnEventType
{
    None,
    Timer,
    Message,
    Error,
    Signal,
    Escalation,
    Compensation,
    Conditional,
    Link,
    Terminate,
    Multiple,
}

/// <summary>BPMN timer definition type.</summary>
public enum TimerType
{
    Date,
    Duration,
    Cycle,
}

/// <summary>Process engine dialect a generated Process API targets.</summary>
public enum BpmnEngine
{
    Zeebe,
    Camunda7,
    Operaton,
}

public sealed record BpmnTimer(TimerType Type, string TimerValue);

public sealed record BpmnErrorDefinition(string Name, string Code);

public sealed record BpmnEscalationDefinition(string Name, string Code);

/// <summary>A variable mapping into or out of a called process; Source and SourceExpression are mutually exclusive.</summary>
public sealed record InputOutputMapping(string Target, string? Source = null, string? SourceExpression = null)
{
    public override string ToString() => Target;
}
