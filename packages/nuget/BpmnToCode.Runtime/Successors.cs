namespace Miragon.BpmnToCode.Runtime;

/// <summary>A sequence flow without its target type, for generic tooling.</summary>
public interface ISequenceFlow
{
    ElementId Id { get; }
    string? Name { get; }
    string? ConditionExpression { get; }
    bool IsDefault { get; }
    IFlowNode Target { get; }
}

/// <summary>One outgoing sequence flow of a flow node: its raw condition expression, default marker and typed target. A process path can step along it to record this flow when several lead to the same element.</summary>
public sealed record SequenceFlow<TTarget>(ElementId Id, string? Name, string? ConditionExpression, bool IsDefault, TTarget Target) : ISequenceFlow, ISuccessor<TTarget>
    where TTarget : IFlowNode
{
    public SequenceFlow(ElementId Id, TTarget Target) : this(Id, null, null, false, Target) { }

    IFlowNode ISequenceFlow.Target => Target;
}

/// <summary>What can follow a flow node in its Next: the SequenceFlows to an element, one SequenceFlow of them, an AttachedBoundaryEvent or an AssociatedCompensationHandler.</summary>
public interface ISuccessor<out TTarget>
    where TTarget : IFlowNode
{
    TTarget Target { get; }
}

/// <summary>The sequence flows of a successor without its target type, for generic tooling.</summary>
public interface ISequenceFlows
{
    System.Collections.Generic.IReadOnlyList<ISequenceFlow> Flows { get; }
}

/// <summary>The sequence flows from one node to the same Target; usually exactly one, reachable as Flow.</summary>
public sealed record SequenceFlows<TTarget>(TTarget Target, System.Collections.Generic.IReadOnlyList<SequenceFlow<TTarget>> Flows) : ISuccessor<TTarget>, ISequenceFlows
    where TTarget : IFlowNode
{
    public System.Collections.Generic.IReadOnlyList<SequenceFlow<TTarget>> Flows { get; } = Flows.Count > 0 ? Flows : throw new System.ArgumentException($"no sequence flow leads to {Target.Id}", nameof(Flows));

    public SequenceFlow<TTarget> Flow => Flows.Count == 1 ? Flows[0] : throw new System.InvalidOperationException($"{Flows.Count} sequence flows lead to {Target.Id}; pick one of Flows");

    System.Collections.Generic.IReadOnlyList<ISequenceFlow> ISequenceFlows.Flows => Flows;
}

public static class SequenceFlows
{
    public static SequenceFlows<TTarget> Single<TTarget>(ElementId flowId, TTarget target, string? name = null, string? conditionExpression = null, bool isDefault = false)
        where TTarget : IFlowNode => new(target, new[] { new SequenceFlow<TTarget>(flowId, name, conditionExpression, isDefault, target) });
}

/// <summary>A boundary event attached to the current node: the token can leave through it, but no sequence flow leads there.</summary>
public sealed record AttachedBoundaryEvent<TTarget>(TTarget Target) : ISuccessor<TTarget>
    where TTarget : IFlowNode;

/// <summary>The compensation handler associated with the current compensation boundary event: it runs when the event triggers, but no sequence flow leads there.</summary>
public sealed record AssociatedCompensationHandler<TTarget>(TTarget Target) : ISuccessor<TTarget>
    where TTarget : IFlowNode;
