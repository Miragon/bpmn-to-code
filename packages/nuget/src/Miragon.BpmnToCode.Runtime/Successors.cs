using System;
using System.Collections.Generic;

namespace Miragon.BpmnToCode.Runtime;

/// <summary>
/// What can follow a flow node in its <c>Next</c>: the <see cref="SequenceFlows{TTarget}"/> to an element, a single
/// <see cref="SequenceFlow{TTarget}"/> picked from them, an <see cref="AttachedBoundaryEvent{TTarget}"/> or an
/// <see cref="AssociatedCompensationHandler{TTarget}"/>. Each leads to a typed <see cref="Target"/>.
/// </summary>
public interface ISuccessor<out TTarget>
    where TTarget : IFlowNode
{
    TTarget Target { get; }
}

/// <summary>A sequence flow without its target type, for generic tooling.</summary>
public interface ISequenceFlow
{
    ElementId Id { get; }

    string? Name { get; }

    string? ConditionExpression { get; }

    bool IsDefault { get; }

    IFlowNode Target { get; }
}

/// <summary>One outgoing sequence flow of a flow node: its raw condition expression, default marker and typed target.</summary>
public sealed record SequenceFlow<TTarget>(ElementId Id, string? Name, string? ConditionExpression, bool IsDefault, TTarget Target) : ISequenceFlow, ISuccessor<TTarget>
    where TTarget : IFlowNode
{
    public SequenceFlow(ElementId Id, TTarget Target) : this(Id, null, null, false, Target) { }

    IFlowNode ISequenceFlow.Target => Target;
}

/// <summary>The sequence flows from one node to the same target without their target type, for generic tooling.</summary>
public interface ISequenceFlows
{
    IReadOnlyList<ISequenceFlow> Flows { get; }
}

/// <summary>The sequence flows from one node to the same <see cref="Target"/>; usually exactly one, reachable as <see cref="Flow"/>.</summary>
public sealed record SequenceFlows<TTarget> : ISuccessor<TTarget>, ISequenceFlows
    where TTarget : IFlowNode
{
    public SequenceFlows(TTarget target, IReadOnlyList<SequenceFlow<TTarget>> flows)
    {
        if (flows.Count == 0) throw new ArgumentException($"at least one sequence flow must lead to {target.Id}", nameof(flows));
        Target = target;
        Flows = flows;
    }

    public TTarget Target { get; }

    public IReadOnlyList<SequenceFlow<TTarget>> Flows { get; }

    IReadOnlyList<ISequenceFlow> ISequenceFlows.Flows => Flows;

    /// <summary>The only sequence flow to <see cref="Target"/>; throws when several lead there, so one has to be picked from <see cref="Flows"/>.</summary>
    public SequenceFlow<TTarget> Flow => Flows.Count == 1 ? Flows[0] : throw new InvalidOperationException($"{Flows.Count} sequence flows lead to {Target.Id}; pick one of Flows");
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
