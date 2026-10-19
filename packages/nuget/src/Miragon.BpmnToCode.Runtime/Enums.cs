namespace Miragon.BpmnToCode.Runtime;

/// <summary>BPMN element type of a flow node; an event's definition is its <see cref="IEvent.EventType"/>.</summary>
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

/// <summary>Event definition of an event: <see cref="None"/> without one, <see cref="Multiple"/> with several.</summary>
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

/// <summary>BPMN timer definition type: <see cref="Duration"/> fires after elapsed time, <see cref="Date"/> at an instant, <see cref="Cycle"/> repeatedly.</summary>
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
