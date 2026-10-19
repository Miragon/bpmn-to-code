export type BpmnEngine = "ZEEBE" | "CAMUNDA_7" | "OPERATON";

export type BpmnElementType =
  | "SERVICE_TASK"
  | "USER_TASK"
  | "RECEIVE_TASK"
  | "SEND_TASK"
  | "SCRIPT_TASK"
  | "MANUAL_TASK"
  | "BUSINESS_RULE_TASK"
  | "TASK"
  | "EXCLUSIVE_GATEWAY"
  | "PARALLEL_GATEWAY"
  | "INCLUSIVE_GATEWAY"
  | "EVENT_BASED_GATEWAY"
  | "COMPLEX_GATEWAY"
  | "SUB_PROCESS"
  | "EVENT_SUB_PROCESS"
  | "TRANSACTION"
  | "CALL_ACTIVITY"
  | "START_EVENT"
  | "END_EVENT"
  | "INTERMEDIATE_CATCH_EVENT"
  | "INTERMEDIATE_THROW_EVENT"
  | "BOUNDARY_EVENT"
  | "UNKNOWN";

export type BpmnEventType =
  | "NONE"
  | "TIMER"
  | "MESSAGE"
  | "ERROR"
  | "SIGNAL"
  | "ESCALATION"
  | "COMPENSATION"
  | "CONDITIONAL"
  | "LINK"
  | "TERMINATE"
  | "MULTIPLE";

export type TimerType = "DATE" | "DURATION" | "CYCLE";

/** Common handle on any flow node, for generic tooling. */
export interface FlowNode {
  readonly id: string;
  readonly elementType: BpmnElementType;
  readonly name: string | null;
}

/** Marks an event node and exposes its event definition kind. */
export interface Event extends FlowNode {
  readonly eventType: BpmnEventType;
}

/** A boundary event: one of its host's successors, reached without a sequence flow. */
export interface BoundaryEvent<HOST extends FlowNode = FlowNode> extends Event {
  readonly attachedTo: HOST;
  readonly isInterrupting: boolean;
}

/** A flow node with successors, exposed behind next. */
export interface HasSuccessors<NEXT = unknown> extends FlowNode {
  readonly next: NEXT;
}

/** A subprocess, whose interior begins with startEvents. */
export interface FlowScope<START = unknown> extends FlowNode {
  readonly startEvents: START;
}

/** A flow node implemented by a job worker of jobType. */
export interface HasJobType extends FlowNode {
  readonly jobType: string;
}

/** A flow node that declares process variables, exposed behind variables. */
export interface HasVariables extends FlowNode {
  readonly variables: VariableDefinitions;
}

/** A call activity, calling the process calledProcess. */
export interface CallActivity extends FlowNode {
  readonly calledProcess: string;
}

/** An event with a timer definition. */
export interface TimerEvent extends Event {
  readonly timer: BpmnTimer;
}

/** A flow node that sends or receives the message. */
export interface HasMessage extends FlowNode {
  readonly message: string;
}

/** An event that throws or catches the signal. */
export interface SignalEvent extends Event {
  readonly signal: string;
}

/** An event that throws or catches the error. */
export interface ErrorEvent extends Event {
  readonly error: BpmnErrorDefinition;
}

/** An event that throws or catches the escalation. */
export interface EscalationEvent extends Event {
  readonly escalation: BpmnEscalationDefinition;
}

/** An intermediate throw or end event that throws a compensation. */
export interface CompensationThrowEvent extends Event {}

export interface BpmnTimer {
  readonly type: TimerType;
  readonly timerValue: string;
}

export interface BpmnErrorDefinition {
  readonly name: string;
  readonly code: string;
}

export interface BpmnEscalationDefinition {
  readonly name: string;
  readonly code: string;
}

/** A variable mapping into or out of a called process; source and sourceExpression are mutually exclusive. */
export interface InputOutputMapping {
  readonly target: string;
  readonly source: string | null;
  readonly sourceExpression: string | null;
}

/** The name of a process variable and whether a node reads it, writes it, or both. */
export interface VariableName {
  readonly direction: "INPUT" | "OUTPUT" | "IN_OUT";
  readonly value: string;
}

/** The variables a node declares: all of them, those it reads and those it writes. */
export interface VariableDefinitions {
  readonly all: readonly VariableName[];
  readonly inputs: readonly VariableName[];
  readonly outputs: readonly VariableName[];
}

/** One outgoing sequence flow, leading to target. */
export interface SequenceFlow<TARGET extends FlowNode = FlowNode> {
  readonly kind: "sequenceFlow";
  readonly id: string;
  readonly name: string | null;
  readonly conditionExpression: string | null;
  readonly isDefault: boolean;
  readonly target: TARGET;
}

/** The sequence flows from one node to the same target; flow is the one when there is exactly one. */
export interface SequenceFlows<TARGET extends FlowNode = FlowNode> {
  readonly kind: "sequenceFlows";
  readonly target: TARGET;
  readonly flows: readonly SequenceFlow<TARGET>[];
  readonly flow: SequenceFlow<TARGET>;
}

/** A boundary event attached to a node; no sequence flow leads there. */
export interface AttachedBoundaryEvent<TARGET extends FlowNode = FlowNode> {
  readonly kind: "attachedBoundaryEvent";
  readonly target: TARGET;
}

/** The compensation handler a compensation boundary event is associated with; no sequence flow leads there. */
export interface AssociatedCompensationHandler<TARGET extends FlowNode = FlowNode> {
  readonly kind: "associatedCompensationHandler";
  readonly target: TARGET;
}

/** What can follow a node in its next: the sequence flows to an element, one flow picked from them, a boundary event or a compensation handler. */
export type Successor<TARGET extends FlowNode = FlowNode> =
  | SequenceFlows<TARGET>
  | SequenceFlow<TARGET>
  | AttachedBoundaryEvent<TARGET>
  | AssociatedCompensationHandler<TARGET>;

export function sequenceFlows<TARGET extends FlowNode>(
  target: TARGET,
  flows: readonly Omit<SequenceFlow<TARGET>, "kind" | "target">[],
): SequenceFlows<TARGET> {
  if (flows.length === 0) {
    throw new Error(`at least one sequence flow must lead to ${target.id}`);
  }
  const withTarget = flows.map((flow): SequenceFlow<TARGET> => ({ kind: "sequenceFlow", ...flow, target }));
  return {
    kind: "sequenceFlows",
    target,
    flows: withTarget,
    get flow(): SequenceFlow<TARGET> {
      const [single] = withTarget;
      if (withTarget.length !== 1 || single === undefined) {
        throw new Error(`${withTarget.length} sequence flows lead to ${target.id}; pick one of flows`);
      }
      return single;
    },
  };
}

export function attachedBoundaryEvent<TARGET extends FlowNode>(target: TARGET): AttachedBoundaryEvent<TARGET> {
  return { kind: "attachedBoundaryEvent", target };
}

export function associatedCompensationHandler<TARGET extends FlowNode>(target: TARGET): AssociatedCompensationHandler<TARGET> {
  return { kind: "associatedCompensationHandler", target };
}

export function input(value: string): VariableName {
  return { direction: "INPUT", value };
}

export function output(value: string): VariableName {
  return { direction: "OUTPUT", value };
}

export function inOut(value: string): VariableName {
  return { direction: "IN_OUT", value };
}

/** The names of the variables the nodes declare, each once, whatever direction a node uses it in. */
export function distinctVariablesOf(nodes: readonly FlowNode[]): string[] {
  const variables = nodes.filter(hasVariables).flatMap((node) => node.variables.all);
  return [...new Set(variables.map((variable) => variable.value))];
}

function hasVariables(node: FlowNode): node is HasVariables {
  return "variables" in node;
}

/** The named variables of a node, together with the lists every VariableDefinitions offers. */
export function variableDefinitions<NAMED extends Record<string, VariableName>>(named: NAMED): NAMED & VariableDefinitions {
  const all = Object.values(named);
  return {
    ...named,
    all,
    inputs: all.filter((variable) => variable.direction !== "OUTPUT"),
    outputs: all.filter((variable) => variable.direction !== "INPUT"),
  };
}
