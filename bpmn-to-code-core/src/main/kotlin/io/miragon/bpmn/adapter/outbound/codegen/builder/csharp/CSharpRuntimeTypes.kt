package io.miragon.bpmn.adapter.outbound.codegen.builder.csharp

import io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpCodeFormat.pascalCase

/**
 * The C# counterpart of `bpmn-to-code-runtime`, emitted verbatim into every generated file as a nested
 * `Runtime` class of the process API. Nesting keeps two generated files in one assembly from clashing and
 * spares consumers a package: they reference `MyProcessApi.Runtime.ElementId`.
 *
 * Kept as one fixed text so the block is identical across files and testable on its own.
 */
internal object CSharpRuntimeTypes {

    const val CLASS_NAME = "Runtime"

    const val BOUNDARY_EVENT = "$CLASS_NAME.IBoundaryEvent"
    const val BPMN_ELEMENT_TYPE = "$CLASS_NAME.BpmnElementType"
    const val BPMN_ENGINE = "$CLASS_NAME.BpmnEngine"
    const val BPMN_ERROR_DEFINITION = "$CLASS_NAME.BpmnErrorDefinition"
    const val BPMN_ESCALATION_DEFINITION = "$CLASS_NAME.BpmnEscalationDefinition"
    const val BPMN_EVENT_TYPE = "$CLASS_NAME.BpmnEventType"
    const val BPMN_TIMER = "$CLASS_NAME.BpmnTimer"
    const val ELEMENT_ID = "$CLASS_NAME.ElementId"
    const val EVENT = "$CLASS_NAME.IEvent"
    const val FLOW_NODE = "$CLASS_NAME.IFlowNode"
    const val INPUT_OUTPUT_MAPPING = "$CLASS_NAME.InputOutputMapping"
    const val MESSAGE_NAME = "$CLASS_NAME.MessageName"
    const val PROCESS_ID = "$CLASS_NAME.ProcessId"
    const val ATTACHED_BOUNDARY_EVENT = "$CLASS_NAME.AttachedBoundaryEvent"
    const val SEQUENCE_FLOW = "$CLASS_NAME.SequenceFlow"
    const val SEQUENCE_FLOWS = "$CLASS_NAME.SequenceFlows"
    const val SIGNAL_NAME = "$CLASS_NAME.SignalName"
    const val TIMER_TYPE = "$CLASS_NAME.TimerType"
    const val VARIABLE_NAME = "$CLASS_NAME.VariableName"

    fun facetInterface(typeName: String): String = "$CLASS_NAME.I$typeName"

    fun enumMember(enumType: String, constantName: String): String = "$enumType.${pascalCase(constantName)}"

    val SOURCE: String = """
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

        /// <summary>Marks a boundary event: one of its host's successors, reached without a sequence flow.</summary>
        public interface IBoundaryEvent : IEvent
        {
        }

        /// <summary>A flow node implemented by a job worker of JobType.</summary>
        public interface IHasJobType : IFlowNode
        {
            string JobType { get; }
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
        public sealed record SequenceFlow<TTarget>(ElementId Id, string? Name, string? ConditionExpression, bool IsDefault, TTarget Target) : ISequenceFlow
            where TTarget : IFlowNode
        {
            public SequenceFlow(ElementId Id, TTarget Target) : this(Id, null, null, false, Target) { }

            IFlowNode ISequenceFlow.Target => Target;
        }

        /// <summary>What can follow a flow node in its Next: the SequenceFlows to an element or an AttachedBoundaryEvent.</summary>
        public interface ISuccessor<out TTarget>
            where TTarget : IFlowNode
        {
            TTarget Target { get; }
        }

        /// <summary>The sequence flows from one node to the same Target; usually exactly one, reachable as Flow.</summary>
        public sealed record SequenceFlows<TTarget>(TTarget Target, System.Collections.Generic.IReadOnlyList<SequenceFlow<TTarget>> Flows) : ISuccessor<TTarget>
            where TTarget : IFlowNode
        {
            public SequenceFlow<TTarget> Flow => Flows.Count == 1 ? Flows[0] : throw new System.InvalidOperationException($"{Flows.Count} sequence flows lead to {Target.Id}; pick one of Flows");
        }

        public static class SequenceFlows
        {
            public static SequenceFlows<TTarget> Single<TTarget>(ElementId flowId, TTarget target, string? name = null, string? conditionExpression = null, bool isDefault = false)
                where TTarget : IFlowNode => new(target, new[] { new SequenceFlow<TTarget>(flowId, name, conditionExpression, isDefault, target) });
        }

        /// <summary>A boundary event attached to the current node: the token can leave through it, but no sequence flow leads there.</summary>
        public sealed record AttachedBoundaryEvent<TTarget>(TTarget Target) : ISuccessor<TTarget>
            where TTarget : IFlowNode;

        public sealed record ElementId(string Value)
        {
            public override string ToString() => Value;
        }

        public sealed record ProcessId(string Value)
        {
            public override string ToString() => Value;
        }

        public sealed record MessageName(string Value)
        {
            public override string ToString() => Value;
        }

        public sealed record SignalName(string Value)
        {
            public override string ToString() => Value;
        }

        /// <summary>A process variable name whose subtype encodes the direction the declaring element uses it in.</summary>
        public abstract record VariableName(string Value)
        {
            public sealed override string ToString() => Value;

            public sealed record Input(string Value) : VariableName(Value);

            public sealed record Output(string Value) : VariableName(Value);

            public sealed record InOut(string Value) : VariableName(Value);
        }

        /// <summary>BPMN element type of a flow node; an event's definition is its EventType.</summary>
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

        /// <summary>Event definition of an event: None without one, Multiple with several.</summary>
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

        /// <summary>BPMN timer definition type.</summary>
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

        public sealed record BpmnTimer(TimerType Type, string TimerValue);

        public sealed record BpmnErrorDefinition(string Name, string Code);

        public sealed record BpmnEscalationDefinition(string Name, string Code);

        /// <summary>A variable mapping into or out of a called process; Source and SourceExpression are mutually exclusive.</summary>
        public sealed record InputOutputMapping(string Target, string? Source = null, string? SourceExpression = null)
        {
            public override string ToString() => Target;
        }
    """.trimIndent()
}
