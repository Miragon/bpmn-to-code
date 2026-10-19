using System.Collections.Generic;
using System.Linq;

namespace Miragon.BpmnToCode.Runtime;

/// <summary>Common handle on any flow node of a generated <c>FlowNodes</c> navigation, for generic tooling.</summary>
public interface IFlowNode
{
    ElementId Id { get; }

    BpmnElementType ElementType { get; }

    string? Name { get; }
}

/// <summary>A flow node with successors, exposed behind <see cref="Next"/>: the node's own successor holder, so each successor stays typed.</summary>
public interface IHasSuccessors<out TNext> : IFlowNode
{
    TNext Next { get; }
}

/// <summary>A subprocess: a flow node whose interior begins with the start elements behind <see cref="Start"/>.</summary>
public interface IFlowScope<out TStart> : IFlowNode
{
    TStart Start { get; }
}

/// <summary>Marks an event node and exposes its event definition kind.</summary>
public interface IEvent : IFlowNode
{
    BpmnEventType EventType { get; }
}

/// <summary>A boundary event attached to <see cref="AttachedTo"/>: one of its host's successors, reached without a sequence flow.</summary>
public interface IBoundaryEvent<out THost> : IEvent
    where THost : IFlowNode
{
    THost AttachedTo { get; }

    bool IsInterrupting { get; }
}

/// <summary>A flow node implemented by a job worker of <see cref="JobType"/>.</summary>
public interface IHasJobType : IFlowNode
{
    string JobType { get; }
}

/// <summary>A flow node that declares process variables, exposed behind <see cref="Variables"/>.</summary>
public interface IHasVariables : IFlowNode
{
    VariableDefinitions Variables { get; }

    /// <summary>The names of the variables the nodes declare, each once, whatever direction a node uses it in.</summary>
    public static IReadOnlyList<string> DistinctVariablesOf(IEnumerable<IFlowNode> nodes) => nodes
        .OfType<IHasVariables>()
        .SelectMany(node => node.Variables.All)
        .Select(variable => variable.Value)
        .Distinct()
        .ToArray();
}

/// <summary>A call activity, calling the process <see cref="CalledProcess"/>.</summary>
public interface ICallActivity : IFlowNode
{
    ProcessId CalledProcess { get; }
}

/// <summary>An event with a <see cref="Timer"/> definition.</summary>
public interface ITimerEvent : IEvent
{
    BpmnTimer Timer { get; }
}

/// <summary>A flow node that sends or receives the <see cref="Message"/>.</summary>
public interface IHasMessage : IFlowNode
{
    MessageName Message { get; }
}

/// <summary>An event that throws or catches the <see cref="Signal"/>.</summary>
public interface ISignalEvent : IEvent
{
    SignalName Signal { get; }
}

/// <summary>An event that throws or catches the <see cref="Error"/>.</summary>
public interface IErrorEvent : IEvent
{
    BpmnErrorDefinition Error { get; }
}

/// <summary>An event that throws or catches the <see cref="Escalation"/>.</summary>
public interface IEscalationEvent : IEvent
{
    BpmnEscalationDefinition Escalation { get; }
}

/// <summary>An event that throws a compensation: an intermediate throw event or an end event.</summary>
public interface ICompensationThrowEvent : IEvent
{
}
