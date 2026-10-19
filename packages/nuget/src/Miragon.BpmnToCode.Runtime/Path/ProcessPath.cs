using System;
using System.Collections.Generic;
using System.Diagnostics.CodeAnalysis;
using System.Linq;

namespace Miragon.BpmnToCode.Runtime.Path;

/// <summary>
/// Starts a <see cref="IProcessPath{TNode}"/> and holds its steps. The edge steps (<see cref="Then"/>, <see cref="Onto"/>)
/// and the subprocess steps (<see cref="Enter{TStart,TTarget}(IProcessPath{IFlowScope{TStart}},Func{TStart,TTarget})"/>,
/// <see cref="Inside"/>) only compile for a real successor or inner start of the current node, so a model change breaks
/// the build at the edge that moved. <see cref="InterruptedBy"/> and <see cref="ThrowingCompensation"/> check the picked
/// successor but let the caller name its carrier; <see cref="JumpTo"/> is the one unchecked step.
/// </summary>
/// <example>
/// <code>
/// var path = ProcessPath.From(FlowNodes.StartEventLeasingRequestReceived.Instance)
///     .Then(next => next.ServiceTaskValidateApplication)
///     .Then(next => next.BusinessRuleTaskCheckCreditRating);
/// Assert.Equal(expectedIds, path.Ids);
/// </code>
/// </example>
public static class ProcessPath
{
    public const string RiskyNavigationDiagnosticId = "BPMN0001";

    /// <summary>Starts a path at <paramref name="start"/>, recording it as the first node.</summary>
    public static IProcessPath<TNode> From<TNode>(TNode start)
        where TNode : IFlowNode => new RecordedPath<TNode>(start, new IFlowNode[] { start }, Array.Empty<ISequenceFlow>());

    /// <summary>
    /// Advances to a successor of the current node and records it. <paramref name="pick"/> gets the node's <c>Next</c>, so
    /// only a real successor compiles. The sequence flow is recorded too when it is unambiguous: a picked
    /// <see cref="SequenceFlow{TTarget}"/>, or <see cref="SequenceFlows{TTarget}"/> with exactly one flow.
    /// </summary>
    public static IProcessPath<TTarget> Then<TNext, TTarget>(this IProcessPath<IHasSuccessors<TNext>> path, Func<TNext, ISuccessor<TTarget>> pick)
        where TTarget : IFlowNode => path.Traverse(pick(path.Current.Next), repeatTimes: 1);

    /// <summary>
    /// Records the same successor <paramref name="repeatTimes"/> times in a row, for a sequential multi-instance activity or a
    /// consecutive self-repeat. The flow leading there is recorded once, as the engine takes it once.
    /// </summary>
    public static IProcessPath<TTarget> ThenMultipleTimes<TNext, TTarget>(this IProcessPath<IHasSuccessors<TNext>> path, int repeatTimes, Func<TNext, ISuccessor<TTarget>> pick)
        where TTarget : IFlowNode => path.Traverse(pick(path.Current.Next), repeatTimes);

    /// <summary>
    /// Moves onto a subprocess successor without recording it, ready to descend with
    /// <see cref="Enter{TStart,TTarget}(IProcessPath{IFlowScope{TStart}},Func{TStart,TTarget})"/> or to walk it with
    /// <see cref="Inside"/>. A subprocess brackets its interior rather than being a point in the ordered flow; the flow into
    /// it is recorded like in <see cref="Then"/>.
    /// </summary>
    public static IProcessPath<TTarget> Onto<TNext, TTarget>(this IProcessPath<IHasSuccessors<TNext>> path, Func<TNext, ISuccessor<TTarget>> subprocess)
        where TTarget : IFlowNode
    {
        var successor = subprocess(path.Current.Next);
        return path.MoveTo(successor.Target, Array.Empty<IFlowNode>(), TakenFlowOf(successor));
    }

    /// <summary>
    /// Descends into the current subprocess and records the picked inner start; <paramref name="pick"/> gets the subprocess's
    /// <c>Start</c>. Leave the interior again through <see cref="Inside"/> (the normal way) or <see cref="InterruptedBy"/>.
    /// </summary>
    public static IProcessPath<TTarget> Enter<TStart, TTarget>(this IProcessPath<IFlowScope<TStart>> path, Func<TStart, TTarget> pick)
        where TTarget : IFlowNode
    {
        var node = pick(path.Current.Start);
        return path.MoveTo(node, new IFlowNode[] { node }, Array.Empty<ISequenceFlow>());
    }

    /// <summary>Descends into the named <paramref name="scope"/> from any position: the re-anchoring form of Enter.</summary>
    public static IProcessPath<TTarget> Enter<TStart, TTarget>(this IProcessPath<IFlowNode> path, IFlowScope<TStart> scope, Func<TStart, TTarget> pick)
        where TTarget : IFlowNode
    {
        var node = pick(scope.Start);
        return path.MoveTo(node, new IFlowNode[] { node }, Array.Empty<ISequenceFlow>());
    }

    /// <summary>
    /// Walks the interior of the current subprocess in <paramref name="walk"/>, which begins on the subprocess and opens it
    /// with Enter, then stays on the subprocess, so the next <see cref="Then"/> is a checked step after it. The walked nodes
    /// and flows are recorded.
    /// </summary>
    public static IProcessPath<TScope> Inside<TScope>(this IProcessPath<TScope> path, Func<IProcessPath<TScope>, IProcessPath<IFlowNode>> walk)
        where TScope : class, IFlowScope<object>
    {
        var interior = walk(new RecordedPath<TScope>(path.Current, Array.Empty<IFlowNode>(), Array.Empty<ISequenceFlow>()));
        return path.MoveTo(path.Current, interior.Nodes, interior.Flows);
    }

    /// <summary>
    /// Leaves an activity or subprocess through one of its boundary events: re-anchors to <paramref name="carrier"/> and
    /// records the picked successor, which only one of the carrier's successors can be.
    /// </summary>
    public static IProcessPath<TTarget> InterruptedBy<TNext, TTarget>(this IProcessPath<IFlowNode> path, IHasSuccessors<TNext> carrier, Func<TNext, ISuccessor<TTarget>> pick)
        where TTarget : IFlowNode => path.Traverse(pick(carrier.Next), repeatTimes: 1);

    /// <summary>
    /// Records a compensation the current event throws: the handler associated with the compensation
    /// <paramref name="boundaryEvent"/> of the compensated activity. Only a compensation throw event can do this, and only a
    /// compensation boundary event offers a handler. The path stays on the current event, as the token continues from there.
    /// The boundary event is left out unless <paramref name="includeBoundaryEvent"/> is set, as only Zeebe reports it as
    /// passed.
    /// </summary>
    public static IProcessPath<TNode> ThrowingCompensation<TNode, TNext, THandler>(
        this IProcessPath<TNode> path,
        IHasSuccessors<TNext> boundaryEvent,
        Func<TNext, AssociatedCompensationHandler<THandler>> pick,
        bool includeBoundaryEvent = false)
        where TNode : class, ICompensationThrowEvent
        where THandler : IFlowNode
    {
        var handler = pick(boundaryEvent.Next).Target;
        var recorded = includeBoundaryEvent ? new IFlowNode[] { boundaryEvent, handler } : new IFlowNode[] { handler };
        return path.MoveTo(path.Current, recorded, Array.Empty<ISequenceFlow>());
    }

    /// <summary>
    /// Re-anchors to any node without recording it and without checking that it can follow: the escape hatch, for example
    /// to step back to a parallel fork. Using it reports <c>BPMN0001</c>, so every use is an explicit opt-in.
    /// </summary>
    [Experimental(RiskyNavigationDiagnosticId)]
    public static IProcessPath<TTarget> JumpTo<TTarget>(this IProcessPath<IFlowNode> path, TTarget node)
        where TTarget : IFlowNode => path.MoveTo(node, Array.Empty<IFlowNode>(), Array.Empty<ISequenceFlow>());

    /// <summary>
    /// Unites separately walked branches into one set without order and duplicates, for parallel branches whose order is
    /// not defined.
    /// </summary>
    public static IReadOnlyList<IFlowNode> NodesOf(params IReadOnlyList<IFlowNode>[] branches) => branches
        .SelectMany(branch => branch)
        .DistinctBy(node => node.Id)
        .ToArray();

    private static IProcessPath<TTarget> Traverse<TTarget>(this IProcessPath<IFlowNode> path, ISuccessor<TTarget> successor, int repeatTimes)
        where TTarget : IFlowNode
    {
        var recorded = Enumerable.Repeat<IFlowNode>(successor.Target, repeatTimes).ToArray();
        return path.MoveTo(successor.Target, recorded, TakenFlowOf(successor));
    }

    private static IProcessPath<TTarget> MoveTo<TTarget>(this IProcessPath<IFlowNode> path, TTarget node, IReadOnlyList<IFlowNode> nodes, IReadOnlyList<ISequenceFlow> flows)
        where TTarget : IFlowNode => new RecordedPath<TTarget>(node, path.Nodes.Concat(nodes).ToArray(), path.Flows.Concat(flows).ToArray());

    private static ISequenceFlow[] TakenFlowOf<TTarget>(ISuccessor<TTarget> successor)
        where TTarget : IFlowNode => successor switch
        {
            ISequenceFlow flow => new[] { flow },
            ISequenceFlows { Flows.Count: 1 } flows => new[] { flows.Flows[0] },
            _ => Array.Empty<ISequenceFlow>(),
        };
}
