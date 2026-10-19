using System.Collections.Generic;

namespace Miragon.BpmnToCode.Runtime.Path;

/// <summary>
/// A compile-checked walk over a generated <c>FlowNodes</c> navigation, standing on <see cref="Current"/> and holding the
/// nodes and sequence flows it passed. Start one with <see cref="ProcessPath.From{TNode}"/> and continue with the steps of
/// <see cref="ProcessPath"/>; the type parameter is covariant, so the steps can tell from it what the current node offers.
/// </summary>
public interface IProcessPath<out TNode>
    where TNode : IFlowNode
{
    TNode Current { get; }

    /// <summary>The recorded nodes, in walk order.</summary>
    IReadOnlyList<IFlowNode> Nodes { get; }

    /// <summary>The recorded sequence flows, in walk order.</summary>
    IReadOnlyList<ISequenceFlow> Flows { get; }

    /// <summary>The element ids of the recorded nodes, in walk order, for the string-array assertions of the engines' test libraries.</summary>
    string[] Ids { get; }

    /// <summary>The element ids of the recorded nodes, each once.</summary>
    string[] DistinctIds { get; }

    /// <summary>The ids of the recorded sequence flows, in walk order.</summary>
    string[] FlowIds { get; }
}
