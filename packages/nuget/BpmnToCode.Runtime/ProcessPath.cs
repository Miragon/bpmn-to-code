namespace Miragon.BpmnToCode.Runtime;

/// <summary>
/// The elements and sequence flows a process path recorded, ready for an engine's flow assertion, for example
/// hasPassedInOrder(path.Ids).
/// </summary>
public abstract class RecordedPath
{
    private protected RecordedPath(System.Collections.Generic.IReadOnlyList<IFlowNode> nodes, System.Collections.Generic.IReadOnlyList<ISequenceFlow> flows)
    {
        Nodes = nodes;
        Flows = flows;
    }

    /// <summary>The recorded elements, in walk order.</summary>
    public System.Collections.Generic.IReadOnlyList<IFlowNode> Nodes { get; }

    /// <summary>The recorded sequence flows, in walk order: one per step whose successor has exactly one flow.</summary>
    public System.Collections.Generic.IReadOnlyList<ISequenceFlow> Flows { get; }

    /// <summary>The ids of the recorded elements, in walk order.</summary>
    public string[] Ids => System.Linq.Enumerable.ToArray(System.Linq.Enumerable.Select(Nodes, node => node.Id.Value));

    /// <summary>The ids of the recorded elements, without duplicates.</summary>
    public string[] DistinctIds => System.Linq.Enumerable.ToArray(System.Linq.Enumerable.Distinct(Ids));

    /// <summary>The ids of the recorded sequence flows, in walk order.</summary>
    public string[] FlowIds => System.Linq.Enumerable.ToArray(System.Linq.Enumerable.Select(Flows, flow => flow.Id.Value));

    /// <summary>Leaves the carrier through one of its boundary events and records the boundary event; the carrier is named freely.</summary>
    public ProcessPath<TTargetNext> InterruptedBy<TCarrierNext, TTargetNext>(IHasSuccessors<TCarrierNext> carrier, System.Func<TCarrierNext, ISuccessor<IHasSuccessors<TTargetNext>>> pick)
    {
        var target = pick(carrier.Next).Target;
        return new ProcessPath<TTargetNext>(target, target.Next, Append(Nodes, target), Flows);
    }

    private protected System.Collections.Generic.IReadOnlyList<IFlowNode> WithCompensation<TBoundaryNext, THandler>(IHasSuccessors<TBoundaryNext> boundaryEvent, System.Func<TBoundaryNext, AssociatedCompensationHandler<THandler>> pick, bool includeBoundaryEvent)
        where THandler : IFlowNode
    {
        var handler = pick(boundaryEvent.Next).Target;
        return includeBoundaryEvent ? Append(Nodes, boundaryEvent, handler) : Append(Nodes, handler);
    }

    private protected System.Collections.Generic.IReadOnlyList<ISequenceFlow> WithFlowOf(object successor)
    {
        return successor is ISequenceFlows sequenceFlows && sequenceFlows.Flows.Count == 1 ? Append(Flows, sequenceFlows.Flows[0]) : Flows;
    }

    private protected static System.Collections.Generic.IReadOnlyList<T> Append<T>(System.Collections.Generic.IReadOnlyList<T> recorded, params T[] items)
    {
        return System.Linq.Enumerable.ToArray(System.Linq.Enumerable.Concat(recorded, items));
    }
}

/// <summary>
/// A compile-checked walk over FlowNodes: every step picks from the current element's Next, so a path the model
/// does not contain does not compile. Start with ProcessPath.From.
/// </summary>
public class ProcessPath<TNext> : RecordedPath
{
    private protected readonly TNext next;

    internal ProcessPath(IFlowNode current, TNext next, System.Collections.Generic.IReadOnlyList<IFlowNode> nodes, System.Collections.Generic.IReadOnlyList<ISequenceFlow> flows)
        : base(nodes, flows)
    {
        Current = current;
        this.next = next;
    }

    /// <summary>The element the walk stands on.</summary>
    public IFlowNode Current { get; }

    /// <summary>Moves to a successor and records it with its sequence flow.</summary>
    public ProcessPath<TTargetNext> Then<TTargetNext>(System.Func<TNext, ISuccessor<IHasSuccessors<TTargetNext>>> pick)
    {
        return ThenMultipleTimes(1, pick);
    }

    /// <summary>Moves to a successor and records it times times in a row, its flow once: a sequential multi-instance activity or a self-repeat.</summary>
    public ProcessPath<TTargetNext> ThenMultipleTimes<TTargetNext>(int times, System.Func<TNext, ISuccessor<IHasSuccessors<TTargetNext>>> pick)
    {
        var successor = pick(next);
        var target = successor.Target;
        var repeated = System.Linq.Enumerable.ToArray(System.Linq.Enumerable.Repeat<IFlowNode>(target, times));
        return new ProcessPath<TTargetNext>(target, target.Next, Append(Nodes, repeated), WithFlowOf(successor));
    }

    /// <summary>Moves onto a subprocess without recording it, to walk its interior with Inside or Enter.</summary>
    public ScopePath<TStart, TTargetNext> Onto<TStart, TTargetNext>(System.Func<TNext, ISuccessor<ISubProcess<TStart, TTargetNext>>> pick)
    {
        var successor = pick(next);
        var target = successor.Target;
        return new ScopePath<TStart, TTargetNext>(target, target.Start, target.Next, Nodes, WithFlowOf(successor));
    }

    /// <summary>Moves to a final successor, such as an end event, and records it.</summary>
    public PathEnd End(System.Func<TNext, ISuccessor<IFlowNode>> pick)
    {
        var successor = pick(next);
        return new PathEnd(Append(Nodes, successor.Target), WithFlowOf(successor));
    }

    /// <summary>Descends into the named subprocess from anywhere and records the picked start element.</summary>
    public ProcessPath<TInnerNext> Enter<TStart, TInnerNext>(IFlowScope<TStart> scope, System.Func<TStart, IHasSuccessors<TInnerNext>> pick)
    {
        var node = pick(scope.Start);
        return new ProcessPath<TInnerNext>(node, node.Next, Append(Nodes, node), Flows);
    }

    /// <summary>Records the handler a compensation boundary event leads to and stays on the current element; includeBoundaryEvent records the boundary event too, which only Zeebe reports as passed.</summary>
    public ProcessPath<TNext> ThrowingCompensation<TBoundaryNext, THandler>(IHasSuccessors<TBoundaryNext> boundaryEvent, System.Func<TBoundaryNext, AssociatedCompensationHandler<THandler>> pick, bool includeBoundaryEvent = false)
        where THandler : IFlowNode
    {
        return new ProcessPath<TNext>(Current, next, WithCompensation(boundaryEvent, pick, includeBoundaryEvent), Flows);
    }

    /// <summary>Re-anchors to any element without checking that it follows and without recording it.</summary>
    [System.Diagnostics.CodeAnalysis.Experimental("BPMNTOCODE001")]
    public ProcessPath<TTargetNext> JumpTo<TTargetNext>(IHasSuccessors<TTargetNext> node)
    {
        return new ProcessPath<TTargetNext>(node, node.Next, Nodes, Flows);
    }
}

/// <summary>A process path standing on a subprocess: Enter descends into it, Inside walks its interior and returns to it.</summary>
public sealed class ScopePath<TStart, TNext> : ProcessPath<TNext>
{
    private readonly TStart start;

    internal ScopePath(IFlowNode current, TStart start, TNext next, System.Collections.Generic.IReadOnlyList<IFlowNode> nodes, System.Collections.Generic.IReadOnlyList<ISequenceFlow> flows)
        : base(current, next, nodes, flows)
    {
        this.start = start;
    }

    /// <summary>Descends into the subprocess and records the picked start element.</summary>
    public ProcessPath<TInnerNext> Enter<TInnerNext>(System.Func<TStart, IHasSuccessors<TInnerNext>> pick)
    {
        var node = pick(start);
        return new ProcessPath<TInnerNext>(node, node.Next, Append(Nodes, node), Flows);
    }

    /// <summary>Walks the interior in block, records what it walked and continues on the subprocess.</summary>
    public ProcessPath<TNext> Inside(System.Func<ScopePath<TStart, TNext>, RecordedPath> block)
    {
        var empty = new ScopePath<TStart, TNext>(Current, start, next, System.Array.Empty<IFlowNode>(), System.Array.Empty<ISequenceFlow>());
        var walked = block(empty);
        return new ProcessPath<TNext>(Current, next, Append(Nodes, System.Linq.Enumerable.ToArray(walked.Nodes)), Append(Flows, System.Linq.Enumerable.ToArray(walked.Flows)));
    }
}

/// <summary>The end of a process path: no further successors, only a compensation or a boundary event to leave through.</summary>
public sealed class PathEnd : RecordedPath
{
    internal PathEnd(System.Collections.Generic.IReadOnlyList<IFlowNode> nodes, System.Collections.Generic.IReadOnlyList<ISequenceFlow> flows)
        : base(nodes, flows)
    {
    }

    /// <summary>Records the handler a compensation boundary event leads to, for an end event that throws a compensation.</summary>
    public PathEnd ThrowingCompensation<TBoundaryNext, THandler>(IHasSuccessors<TBoundaryNext> boundaryEvent, System.Func<TBoundaryNext, AssociatedCompensationHandler<THandler>> pick, bool includeBoundaryEvent = false)
        where THandler : IFlowNode
    {
        return new PathEnd(WithCompensation(boundaryEvent, pick, includeBoundaryEvent), Flows);
    }
}

public static class ProcessPath
{
    /// <summary>Starts a path at start and records it.</summary>
    public static ProcessPath<TNext> From<TNext>(IHasSuccessors<TNext> start)
    {
        return new ProcessPath<TNext>(start, start.Next, new IFlowNode[] { start }, System.Array.Empty<ISequenceFlow>());
    }

    /// <summary>Starts a path at a subprocess and records it.</summary>
    public static ScopePath<TStart, TNext> From<TStart, TNext>(ISubProcess<TStart, TNext> start)
    {
        return new ScopePath<TStart, TNext>(start, start.Start, start.Next, new IFlowNode[] { start }, System.Array.Empty<ISequenceFlow>());
    }

    /// <summary>Unites separately walked parallel branches into one set without order and duplicates.</summary>
    public static System.Collections.Generic.IReadOnlyList<IFlowNode> NodesOf(params System.Collections.Generic.IReadOnlyList<IFlowNode>[] branches)
    {
        return System.Linq.Enumerable.ToArray(System.Linq.Enumerable.Distinct(System.Linq.Enumerable.SelectMany(branches, branch => branch)));
    }
}
