package io.miragon.bpmn.adapter.outbound.codegen.writer

/**
 * The C# counterpart of `bpmn-to-code-runtime`, emitted verbatim into every generated file as a nested
 * `Runtime` class of the process API. Nesting keeps two generated files in one assembly from clashing and
 * spares consumers a package: they reference `MyProcessApi.Runtime.ElementId`.
 *
 * Kept as one fixed text so the block is identical across files and testable on its own.
 */
internal object CSharpRuntimeTypes {

    const val CLASS_NAME = "Runtime"

    val SOURCE: String = """
        /// <summary>Common handle on any Flow node, for generic tooling.</summary>
        public interface IFlowNode
        {
            ElementId Id { get; }
            string ElementType { get; }
            string? Name { get; }
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

        /// <summary>One outgoing sequence flow of a Flow node: its raw condition expression, default marker and typed target.</summary>
        public sealed record SequenceFlow<TTarget>(ElementId Id, string? Name, string? ConditionExpression, bool IsDefault, TTarget Target) : ISequenceFlow
            where TTarget : IFlowNode
        {
            IFlowNode ISequenceFlow.Target => Target;
        }

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
            public override string ToString() => Value;

            public sealed record Input(string Value) : VariableName(Value);

            public sealed record Output(string Value) : VariableName(Value);

            public sealed record InOut(string Value) : VariableName(Value);
        }

        public sealed record BpmnTimer(string Type, string TimerValue);

        public sealed record BpmnError(string Name, string Code);

        public sealed record BpmnEscalation(string Name, string Code);

        /// <summary>A variable mapping into or out of a called process; Source and SourceExpression are mutually exclusive.</summary>
        public sealed record InputOutputMapping(string Target, string? Source = null, string? SourceExpression = null)
        {
            public override string ToString() => Target;
        }
    """.trimIndent()
}
