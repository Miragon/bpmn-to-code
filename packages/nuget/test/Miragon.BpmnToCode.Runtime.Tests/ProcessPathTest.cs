using System;
using System.Linq;
using Miragon.BpmnToCode.Runtime.Path;
using Xunit;

namespace Miragon.BpmnToCode.Runtime.Tests;

/// <summary>
/// The steps of <see cref="ProcessPath"/> over a small hand-built graph. <see cref="ProcessPathApiTest"/> walks a generated
/// API.
/// </summary>
public class ProcessPathTest
{
    [Fact]
    public void Then_records_each_hop_and_exposes_ids_and_nodes_in_order()
    {
        var path = ProcessPath.From(Start.Instance).Then(next => next.Mid).Then(next => next.End);

        Assert.Equal(new[] { "Start", "Mid", "End" }, path.Ids);
        Assert.Equal(new[] { "Start", "Mid", "End" }, path.Nodes.Select(node => node.Id.Value));
        Assert.Same(End.Instance, path.Current);
    }

    [Fact]
    public void Then_along_a_transition_with_one_flow_records_the_flow()
    {
        var path = ProcessPath.From(Start.Instance).Then(next => next.Mid);

        Assert.Equal(new[] { "flow_startToMid" }, path.FlowIds);
        Assert.Same(Mid.Instance, path.Current);
    }

    [Fact]
    public void Then_along_a_transition_with_several_flows_records_none_of_them()
    {
        var path = ProcessPath.From(Start.Instance).Then(next => next.Mid).Then(next => next.End);

        Assert.Equal(new[] { "flow_startToMid" }, path.FlowIds);
    }

    [Fact]
    public void Then_along_a_flow_picked_from_a_transition_records_exactly_that_flow()
    {
        var path = ProcessPath.From(Start.Instance)
            .Then(next => next.Mid)
            .Then(next => next.End.Flows.Single(flow => flow.ConditionExpression == "=b"));

        Assert.Equal(new[] { "Start", "Mid", "End" }, path.Ids);
        Assert.Equal(new[] { "flow_startToMid", "flow_midToEndB" }, path.FlowIds);
    }

    [Fact]
    public void The_flow_of_a_transition_with_several_flows_is_ambiguous()
    {
        var exception = Assert.Throws<InvalidOperationException>(() => Mid.Instance.Next.End.Flow);

        Assert.Contains("pick one of Flows", exception.Message);
    }

    [Fact]
    public void Sequence_flows_need_at_least_one_flow()
    {
        var exception = Assert.Throws<ArgumentException>(() => new SequenceFlows<Mid>(Mid.Instance, Array.Empty<SequenceFlow<Mid>>()));

        Assert.Contains("at least one sequence flow", exception.Message);
    }

    [Fact]
    public void ThenMultipleTimes_records_the_same_node_several_times_and_its_flow_once()
    {
        var path = ProcessPath.From(Start.Instance).ThenMultipleTimes(3, next => next.Mid);

        Assert.Equal(new[] { "Start", "Mid", "Mid", "Mid" }, path.Ids);
        Assert.Equal(new[] { "Start", "Mid" }, path.DistinctIds);
        Assert.Equal(new[] { "flow_startToMid" }, path.FlowIds);
    }

    [Fact]
    public void Enter_descends_into_the_current_node_and_records_the_inner_start()
    {
        var path = ProcessPath.From(Sub.Instance).Enter(start => start.InnerStart);

        Assert.Equal(new[] { "Sub", "InnerStart" }, path.Ids);
        Assert.Same(InnerStart.Instance, path.Current);
    }

    [Fact]
    public void Onto_advances_to_the_subprocess_without_recording_it()
    {
        var path = ProcessPath.From(Start.Instance).Onto(next => next.Sub).Enter(start => start.InnerStart);

        Assert.Equal(new[] { "Start", "InnerStart" }, path.Ids);
        Assert.Equal(new[] { "flow_startToSub" }, path.FlowIds);
    }

    [Fact]
    public void Enter_with_an_explicit_scope_descends_from_any_position()
    {
        var path = ProcessPath.From(Start.Instance).Enter(Sub.Instance, start => start.InnerStart);

        Assert.Equal(new[] { "Start", "InnerStart" }, path.Ids);
    }

    [Fact]
    public void Inside_walks_the_interior_and_resumes_on_the_subprocess()
    {
        var path = ProcessPath.From(Start.Instance)
            .Onto(next => next.Sub)
            .Inside(sub => sub.Enter(start => start.InnerStart))
            .Then(next => next.End);

        Assert.Equal(new[] { "Start", "InnerStart", "End" }, path.Ids);
        Assert.Equal(new[] { "flow_startToSub", "flow_subToEnd" }, path.FlowIds);
        Assert.Same(End.Instance, path.Current);
    }

    [Fact]
    public void InterruptedBy_leaves_through_a_boundary_event_and_records_no_flow()
    {
        var path = ProcessPath.From(Sub.Instance)
            .Enter(start => start.InnerStart)
            .InterruptedBy(Sub.Instance, next => next.Boundary);

        Assert.Equal(new[] { "Sub", "InnerStart", "Boundary" }, path.Ids);
        Assert.Empty(path.FlowIds);
        Assert.Same(Boundary.Instance, path.Current);
    }

    [Fact]
    public void Then_onto_the_compensation_handler_of_a_boundary_event_records_the_handler()
    {
        var path = ProcessPath.From(Boundary.Instance).Then(next => next.Handler);

        Assert.Equal(new[] { "Boundary", "Handler" }, path.Ids);
        Assert.Empty(path.FlowIds);
    }

    [Fact]
    public void ThrowingCompensation_records_the_boundary_event_and_its_handler_when_asked_to_and_stays_on_the_event()
    {
        var path = ProcessPath.From(Throw.Instance)
            .ThrowingCompensation(Boundary.Instance, next => next.Handler, includeBoundaryEvent: true)
            .Then(next => next.End);

        Assert.Equal(new[] { "Throw", "Boundary", "Handler", "End" }, path.Ids);
        Assert.Equal(new[] { "flow_throwToEnd" }, path.FlowIds);
    }

    [Fact]
    public void ThrowingCompensation_records_the_handler_without_its_boundary_event_by_default()
    {
        var path = ProcessPath.From(Throw.Instance).ThrowingCompensation(Boundary.Instance, next => next.Handler);

        Assert.Equal(new[] { "Throw", "Handler" }, path.Ids);
        Assert.Same(Throw.Instance, path.Current);
    }

    [Fact]
    public void NodesOf_unites_branches_without_duplicates()
    {
        var shortBranch = ProcessPath.From(Start.Instance).Then(next => next.Mid).Nodes;
        var longBranch = ProcessPath.From(Start.Instance).Then(next => next.Mid).Then(next => next.End).Nodes;

        Assert.Equal(new[] { "Start", "Mid", "End" }, ProcessPath.NodesOf(shortBranch, longBranch).Select(node => node.Id.Value));
    }

    [Fact]
    public void JumpTo_re_anchors_without_recording_and_keeps_the_flows_so_far()
    {
        var path = ProcessPath.From(Start.Instance).Then(next => next.Mid).JumpTo(Sub.Instance).Then(next => next.End);

        Assert.Equal(new[] { "Start", "Mid", "End" }, path.Ids);
        Assert.Equal(new[] { "flow_startToMid", "flow_subToEnd" }, path.FlowIds);
    }

    [Fact]
    public void DistinctVariablesOf_lists_each_variable_once()
    {
        var variables = IHasVariables.DistinctVariablesOf(new IFlowNode[] { Mid.Instance, Start.Instance, Mid.Instance });

        Assert.Equal(new[] { "amount", "approved" }, variables);
    }

    [Fact]
    public void Variable_definitions_split_into_inputs_and_outputs()
    {
        var variables = Mid.Instance.Variables;

        Assert.Equal(new[] { "amount" }, variables.Inputs.Select(variable => variable.Value));
        Assert.Equal(new[] { "approved" }, variables.Outputs.Select(variable => variable.Value));
    }

    private abstract class Node : IFlowNode
    {
        protected Node(string id, BpmnElementType elementType)
        {
            Id = new ElementId(id);
            ElementType = elementType;
        }

        public ElementId Id { get; }

        public BpmnElementType ElementType { get; }

        public string? Name => null;
    }

    private sealed class End : Node
    {
        public static readonly End Instance = new();

        private End() : base("End", BpmnElementType.EndEvent) { }
    }

    private sealed class Handler : Node
    {
        public static readonly Handler Instance = new();

        private Handler() : base("Handler", BpmnElementType.ServiceTask) { }
    }

    private sealed class InnerStart : Node
    {
        public static readonly InnerStart Instance = new();

        private InnerStart() : base("InnerStart", BpmnElementType.StartEvent) { }
    }

    private sealed class Throw : Node, IHasSuccessors<Throw.Successors>, ICompensationThrowEvent
    {
        public static readonly Throw Instance = new();

        private Throw() : base("Throw", BpmnElementType.IntermediateThrowEvent) { }

        public BpmnEventType EventType => BpmnEventType.Compensation;

        public Successors Next => new();

        public sealed class Successors
        {
            public SequenceFlows<End> End => SequenceFlows.Single(new ElementId("flow_throwToEnd"), Tests.ProcessPathTest.End.Instance);
        }
    }

    private sealed class Boundary : Node, IHasSuccessors<Boundary.Successors>, IBoundaryEvent<Sub>
    {
        public static readonly Boundary Instance = new();

        private Boundary() : base("Boundary", BpmnElementType.BoundaryEvent) { }

        public BpmnEventType EventType => BpmnEventType.Compensation;

        public Sub AttachedTo => Sub.Instance;

        public bool IsInterrupting => true;

        public Successors Next => new();

        public sealed class Successors
        {
            public AssociatedCompensationHandler<Handler> Handler => new(Tests.ProcessPathTest.Handler.Instance);
        }
    }

    private sealed class Mid : Node, IHasSuccessors<Mid.Successors>, IHasVariables
    {
        public static readonly Mid Instance = new();

        private Mid() : base("Mid", BpmnElementType.Task) { }

        public Successors Next => new();

        public VariableDefinitions Variables { get; } = new MidVariables();

        public sealed class Successors
        {
            public SequenceFlows<End> End => new(
                Tests.ProcessPathTest.End.Instance,
                new[]
                {
                    new SequenceFlow<End>(new ElementId("flow_midToEndA"), null, "=a", false, Tests.ProcessPathTest.End.Instance),
                    new SequenceFlow<End>(new ElementId("flow_midToEndB"), null, "=b", false, Tests.ProcessPathTest.End.Instance),
                });
        }

        private sealed class MidVariables : VariableDefinitions
        {
            public override System.Collections.Generic.IReadOnlyList<VariableName> All { get; } =
                new VariableName[] { new VariableName.Input("amount"), new VariableName.Output("approved") };
        }
    }

    private sealed class Start : Node, IHasSuccessors<Start.Successors>
    {
        public static readonly Start Instance = new();

        private Start() : base("Start", BpmnElementType.StartEvent) { }

        public Successors Next => new();

        public sealed class Successors
        {
            public SequenceFlows<Mid> Mid => SequenceFlows.Single(new ElementId("flow_startToMid"), Tests.ProcessPathTest.Mid.Instance);

            public SequenceFlows<Sub> Sub => SequenceFlows.Single(new ElementId("flow_startToSub"), Tests.ProcessPathTest.Sub.Instance);
        }
    }

    private sealed class Sub : Node, IHasSuccessors<Sub.Successors>, IFlowScope<Sub.Interior>
    {
        public static readonly Sub Instance = new();

        private Sub() : base("Sub", BpmnElementType.SubProcess) { }

        public Successors Next => new();

        public Interior Start => new();

        public sealed class Successors
        {
            public SequenceFlows<End> End => SequenceFlows.Single(new ElementId("flow_subToEnd"), Tests.ProcessPathTest.End.Instance);

            public AttachedBoundaryEvent<Boundary> Boundary => new(Tests.ProcessPathTest.Boundary.Instance);
        }

        public sealed class Interior
        {
            public InnerStart InnerStart => Tests.ProcessPathTest.InnerStart.Instance;
        }
    }
}
