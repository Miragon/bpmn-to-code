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

/**
 * Any element of FlowNodes, for code that handles nodes generically.
 */
export interface FlowNode {
  readonly id: string;
  readonly elementType: BpmnElementType;
  readonly name: string | null;
}

/**
 * An event, with the kind of its event definition.
 */
export interface Event extends FlowNode {
  readonly eventType: BpmnEventType;
}

/**
 * A boundary event: one of the successors of its host, reached without a sequence flow.
 */
export interface BoundaryEvent<HOST extends FlowNode = FlowNode> extends Event {
  readonly attachedTo: HOST;
  readonly isInterrupting: boolean;
}

/**
 * A node with successors, named after the elements they lead to.
 */
export interface HasSuccessors<NEXT = unknown> extends FlowNode {
  readonly next: NEXT;
}

/**
 * A subprocess, whose interior begins with startEvents.
 */
export interface FlowScope<START = unknown> extends FlowNode {
  readonly startEvents: START;
}

export interface HasJobType extends FlowNode {
  readonly jobType: string;
}

export interface HasVariables extends FlowNode {
  readonly variables: VariableDefinitions;
}

export interface CallActivity extends FlowNode {
  readonly calledProcess: string;
}

export interface TimerEvent extends Event {
  readonly timer: BpmnTimer;
}

export interface HasMessage extends FlowNode {
  readonly message: string;
}

export interface SignalEvent extends Event {
  readonly signal: string;
}

export interface ErrorEvent extends Event {
  readonly error: BpmnErrorDefinition;
}

export interface EscalationEvent extends Event {
  readonly escalation: BpmnEscalationDefinition;
}

/**
 * An intermediate throw or end event that throws a compensation.
 */
export interface CompensationThrowEvent extends Event {
  readonly throwsCompensation: true;
}

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

/**
 * A variable mapping into or out of a called process; source and sourceExpression exclude each other.
 */
export interface InputOutputMapping {
  readonly target: string;
  readonly source: string | null;
  readonly sourceExpression: string | null;
}

export type VariableDirection = "INPUT" | "OUTPUT" | "IN_OUT";

/**
 * The name of a process variable and whether a node reads it, writes it or both; it converts to its name.
 */
export class VariableName<DIRECTION extends VariableDirection = VariableDirection> {
  constructor(
    readonly value: string,
    readonly direction: DIRECTION,
  ) {}

  toString(): string {
    return this.value;
  }
}

export function input(name: string): VariableName<"INPUT"> {
  return new VariableName(name, "INPUT");
}

export function output(name: string): VariableName<"OUTPUT"> {
  return new VariableName(name, "OUTPUT");
}

export function inOut(name: string): VariableName<"IN_OUT"> {
  return new VariableName(name, "IN_OUT");
}

/**
 * The variables a node declares: all of them, those it reads and those it writes.
 */
export interface VariableDefinitions {
  readonly all: readonly VariableName[];
  readonly inputs: readonly VariableName[];
  readonly outputs: readonly VariableName[];
}

export function variableDefinitions<NAMED extends Record<string, VariableName>>(named: NAMED): NAMED & VariableDefinitions {
  const all = Object.values(named);
  const inputs = all.filter((variable) => variable.direction !== "OUTPUT");
  const outputs = all.filter((variable) => variable.direction !== "INPUT");
  return { ...named, all, inputs, outputs };
}

/**
 * One outgoing sequence flow, leading to target.
 */
export interface SequenceFlow<TARGET extends FlowNode = FlowNode> {
  readonly kind: "sequenceFlow";
  readonly id: string;
  readonly name: string | null;
  readonly conditionExpression: string | null;
  readonly isDefault: boolean;
  readonly target: TARGET;
}

/**
 * The sequence flows from one node to the same target; flow is the one when there is exactly one.
 */
export interface SequenceFlows<TARGET extends FlowNode = FlowNode> {
  readonly kind: "sequenceFlows";
  readonly target: TARGET;
  readonly flows: readonly SequenceFlow<TARGET>[];
  readonly flow: SequenceFlow<TARGET>;
}

/**
 * A boundary event attached to the node; no sequence flow leads there.
 */
export interface AttachedBoundaryEvent<TARGET extends FlowNode = FlowNode> {
  readonly kind: "attachedBoundaryEvent";
  readonly target: TARGET;
}

/**
 * The compensation handler a compensation boundary event is associated with; no sequence flow leads there.
 */
export interface AssociatedCompensationHandler<TARGET extends FlowNode = FlowNode> {
  readonly kind: "associatedCompensationHandler";
  readonly target: TARGET;
}

export type Successor<TARGET extends FlowNode = FlowNode> =
  | SequenceFlow<TARGET>
  | SequenceFlows<TARGET>
  | AttachedBoundaryEvent<TARGET>
  | AssociatedCompensationHandler<TARGET>;

export interface FlowData {
  readonly id: string;
  readonly name?: string;
  readonly conditionExpression?: string;
  readonly isDefault?: boolean;
}

export function sequenceFlows<TARGET extends FlowNode>(target: TARGET, ...flows: FlowData[]): SequenceFlows<TARGET> {
  const flowsToTarget = flows.map((flow): SequenceFlow<TARGET> => ({
    kind: "sequenceFlow",
    id: flow.id,
    name: flow.name ?? null,
    conditionExpression: flow.conditionExpression ?? null,
    isDefault: flow.isDefault ?? false,
    target,
  }));
  return {
    kind: "sequenceFlows",
    target,
    flows: flowsToTarget,
    get flow(): SequenceFlow<TARGET> {
      const [single] = flowsToTarget;
      if (flowsToTarget.length !== 1 || single === undefined) {
        throw new Error(`${flowsToTarget.length} sequence flows lead to ${target.id}; pick one of flows`);
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
