namespace Miragon.BpmnToCode.Runtime;

/// <summary>Common handle on any flow node, for generic tooling.</summary>
public interface IFlowNode
{
    ElementId Id { get; }
    BpmnElementType ElementType { get; }
    string? Name { get; }
}

/// <summary>Marks an event node and exposes its event definition kind.</summary>
public interface IEvent : IFlowNode
{
    BpmnEventType EventType { get; }
}

/// <summary>A flow node with successors, reached as Next; what a process path walks along.</summary>
public interface IHasSuccessors<out TNext> : IFlowNode
{
    TNext Next { get; }
}

/// <summary>A subprocess: the start elements of its interior, reached as Start.</summary>
public interface IFlowScope<out TStart> : IFlowNode
{
    TStart Start { get; }
}

/// <summary>A subprocess with successors: a process path can walk its interior and continue after it.</summary>
public interface ISubProcess<out TStart, out TNext> : IFlowScope<TStart>, IHasSuccessors<TNext>
{
}

/// <summary>Marks a boundary event: one of its host's successors, reached without a sequence flow.</summary>
public interface IBoundaryEvent : IEvent
{
}

/// <summary>A flow node implemented by a job worker of JobType.</summary>
public interface IHasJobType : IFlowNode
{
    string JobType { get; }
}

/// <summary>A flow node that declares process variables, exposed behind Variables.</summary>
public interface IHasVariables : IFlowNode
{
    VariableDefinitions Variables { get; }
}

/// <summary>A call activity, calling the process CalledProcess.</summary>
public interface ICallActivity : IFlowNode
{
    ProcessId CalledProcess { get; }
}

/// <summary>An event with a Timer definition.</summary>
public interface ITimerEvent : IEvent
{
    BpmnTimer Timer { get; }
}

/// <summary>A flow node that sends or receives the Message.</summary>
public interface IHasMessage : IFlowNode
{
    MessageName Message { get; }
}

/// <summary>An event that throws or catches the Signal.</summary>
public interface ISignalEvent : IEvent
{
    SignalName Signal { get; }
}

/// <summary>An event that throws or catches the Error.</summary>
public interface IErrorEvent : IEvent
{
    BpmnErrorDefinition Error { get; }
}

/// <summary>An event that throws or catches the Escalation.</summary>
public interface IEscalationEvent : IEvent
{
    BpmnEscalationDefinition Escalation { get; }
}

/// <summary>An event that throws a compensation: an intermediate throw event or an end event.</summary>
public interface ICompensationThrowEvent : IEvent
{
}
