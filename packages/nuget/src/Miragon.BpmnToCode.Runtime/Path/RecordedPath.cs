using System.Collections.Generic;
using System.Linq;

namespace Miragon.BpmnToCode.Runtime.Path;

internal sealed class RecordedPath<TNode> : IProcessPath<TNode>
    where TNode : IFlowNode
{
    public RecordedPath(TNode current, IReadOnlyList<IFlowNode> nodes, IReadOnlyList<ISequenceFlow> flows)
    {
        Current = current;
        Nodes = nodes;
        Flows = flows;
    }

    public TNode Current { get; }

    public IReadOnlyList<IFlowNode> Nodes { get; }

    public IReadOnlyList<ISequenceFlow> Flows { get; }

    public string[] Ids => Nodes.Select(node => node.Id.Value).ToArray();

    public string[] DistinctIds => Ids.Distinct().ToArray();

    public string[] FlowIds => Flows.Select(flow => flow.Id.Value).ToArray();

    public override string ToString() => string.Join(" -> ", Ids);
}
