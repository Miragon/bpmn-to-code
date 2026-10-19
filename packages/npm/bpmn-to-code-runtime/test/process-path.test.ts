import assert from "node:assert/strict";
import { test } from "node:test";
import {
  associatedCompensationHandler,
  attachedBoundaryEvent,
  distinctVariablesOf,
  input,
  output,
  ProcessPath,
  RiskyNavigation,
  sequenceFlows,
  variableDefinitions,
  type BoundaryEvent,
  type CompensationThrowEvent,
  type FlowNode,
  type FlowScope,
  type HasSuccessors,
  type HasVariables,
} from "@miragon/bpmn-to-code-runtime";

const flow = { name: null, conditionExpression: null, isDefault: false };

const End = { id: "End", elementType: "END_EVENT", name: null } as const satisfies FlowNode;
const Handler = { id: "Handler", elementType: "SERVICE_TASK", name: null } as const satisfies FlowNode;
const InnerStart = { id: "InnerStart", elementType: "START_EVENT", name: null } as const satisfies FlowNode;

const Throw = {
  id: "Throw",
  elementType: "INTERMEDIATE_THROW_EVENT",
  eventType: "COMPENSATION",
  name: null,
  get next() {
    return { get end() { return sequenceFlows(End, [{ id: "flow_throwToEnd", ...flow }]); } };
  },
} as const satisfies CompensationThrowEvent & HasSuccessors;

const Mid = {
  id: "Mid",
  elementType: "TASK",
  name: null,
  variables: variableDefinitions({ AMOUNT: input("amount"), APPROVED: output("approved") }),
  get next() {
    return {
      get end() {
        return sequenceFlows(End, [
          { id: "flow_midToEndA", ...flow, conditionExpression: "=a" },
          { id: "flow_midToEndB", ...flow, conditionExpression: "=b" },
        ]);
      },
    };
  },
} as const satisfies HasSuccessors & HasVariables;

const Sub = {
  id: "Sub",
  elementType: "SUB_PROCESS",
  name: null,
  get next() {
    return {
      get end() { return sequenceFlows(End, [{ id: "flow_subToEnd", ...flow }]); },
      get boundary() { return attachedBoundaryEvent(Boundary); },
    };
  },
  get startEvents() {
    return { get innerStart() { return InnerStart; } };
  },
} as const satisfies HasSuccessors & FlowScope;

const Boundary = {
  id: "Boundary",
  elementType: "BOUNDARY_EVENT",
  eventType: "COMPENSATION",
  name: null,
  attachedTo: Sub,
  isInterrupting: true,
  get next() {
    return { get handler() { return associatedCompensationHandler(Handler); } };
  },
} as const satisfies BoundaryEvent & HasSuccessors;

const Start = {
  id: "Start",
  elementType: "START_EVENT",
  name: null,
  get next() {
    return {
      get mid() { return sequenceFlows(Mid, [{ id: "flow_startToMid", ...flow }]); },
      get sub() { return sequenceFlows(Sub, [{ id: "flow_startToSub", ...flow }]); },
    };
  },
} as const satisfies HasSuccessors;

test("then records each hop and exposes ids and nodes in order", () => {
  const path = ProcessPath.from(Start).then((next) => next.mid).then((next) => next.end);

  assert.deepEqual(path.ids, ["Start", "Mid", "End"]);
  assert.deepEqual(path.nodes.map((node) => node.id), ["Start", "Mid", "End"]);
  assert.equal(path.current, End);
});

test("then along a transition with one flow records the flow, with several none", () => {
  const path = ProcessPath.from(Start).then((next) => next.mid).then((next) => next.end);

  assert.deepEqual(path.flowIds, ["flow_startToMid"]);
});

test("then along a flow picked from a transition records exactly that flow", () => {
  const path = ProcessPath.from(Mid).then((next) => {
    const picked = next.end.flows.find((candidate) => candidate.conditionExpression === "=b");
    assert.ok(picked);
    return picked;
  });

  assert.deepEqual(path.ids, ["Mid", "End"]);
  assert.deepEqual(path.flowIds, ["flow_midToEndB"]);
});

test("the flow of a transition with several flows is ambiguous", () => {
  assert.throws(() => Mid.next.end.flow, /pick one of flows/);
});

test("sequence flows need at least one flow", () => {
  assert.throws(() => sequenceFlows(Mid, []), /at least one sequence flow/);
});

test("thenMultipleTimes records the same node several times and its flow once", () => {
  const path = ProcessPath.from(Start).thenMultipleTimes(3, (next) => next.mid);

  assert.deepEqual(path.ids, ["Start", "Mid", "Mid", "Mid"]);
  assert.deepEqual(path.distinctIds, ["Start", "Mid"]);
  assert.deepEqual(path.flowIds, ["flow_startToMid"]);
});

test("enter descends into the current node and records the inner start", () => {
  const path = ProcessPath.from(Sub).enter((start) => start.innerStart);

  assert.deepEqual(path.ids, ["Sub", "InnerStart"]);
  assert.equal(path.current, InnerStart);
});

test("onto advances to the subprocess without recording it", () => {
  const path = ProcessPath.from(Start).onto((next) => next.sub).enter((start) => start.innerStart);

  assert.deepEqual(path.ids, ["Start", "InnerStart"]);
  assert.deepEqual(path.flowIds, ["flow_startToSub"]);
});

test("enter with an explicit scope descends from any position", () => {
  const path = ProcessPath.from(Start).enter(Sub, (start) => start.innerStart);

  assert.deepEqual(path.ids, ["Start", "InnerStart"]);
});

test("inside walks the interior and resumes on the subprocess", () => {
  const path = ProcessPath.from(Start)
    .onto((next) => next.sub)
    .inside((sub) => sub.enter((start) => start.innerStart))
    .then((next) => next.end);

  assert.deepEqual(path.ids, ["Start", "InnerStart", "End"]);
  assert.deepEqual(path.flowIds, ["flow_startToSub", "flow_subToEnd"]);
});

test("interruptedBy leaves through a boundary event and records no flow", () => {
  const path = ProcessPath.from(Sub).enter((start) => start.innerStart).interruptedBy(Sub, (next) => next.boundary);

  assert.deepEqual(path.ids, ["Sub", "InnerStart", "Boundary"]);
  assert.deepEqual(path.flowIds, []);
  assert.equal(path.current, Boundary);
});

test("throwingCompensation records the boundary event and its handler when asked to and stays on the event", () => {
  const path = ProcessPath.from(Throw)
    .throwingCompensation(Boundary, (next) => next.handler, { includeBoundaryEvent: true })
    .then((next) => next.end);

  assert.deepEqual(path.ids, ["Throw", "Boundary", "Handler", "End"]);
  assert.deepEqual(path.flowIds, ["flow_throwToEnd"]);
});

test("throwingCompensation records the handler without its boundary event by default", () => {
  const path = ProcessPath.from(Throw).throwingCompensation(Boundary, (next) => next.handler);

  assert.deepEqual(path.ids, ["Throw", "Handler"]);
  assert.equal(path.current, Throw);
});

test("nodesOf unites branches without duplicates", () => {
  const shortBranch = ProcessPath.from(Start).then((next) => next.mid).nodes;
  const longBranch = ProcessPath.from(Start).then((next) => next.mid).then((next) => next.end).nodes;

  assert.deepEqual(ProcessPath.nodesOf(shortBranch, longBranch).map((node) => node.id), ["Start", "Mid", "End"]);
});

test("jumpTo re-anchors without recording and keeps the flows so far", () => {
  const path = ProcessPath.from(Start).then((next) => next.mid).jumpTo(Sub, RiskyNavigation).then((next) => next.end);

  assert.deepEqual(path.ids, ["Start", "Mid", "End"]);
  assert.deepEqual(path.flowIds, ["flow_startToMid", "flow_subToEnd"]);
});

test("variable definitions split into inputs and outputs and list each variable once", () => {
  assert.deepEqual(Mid.variables.inputs.map((variable) => variable.value), ["amount"]);
  assert.deepEqual(Mid.variables.outputs.map((variable) => variable.value), ["approved"]);
  assert.deepEqual(distinctVariablesOf([Mid, Start, Mid]), ["amount", "approved"]);
});

/** Never called: tsc fails the test build when one of these steps compiles. */
export function stepsOnlyCompileForWhatTheCurrentNodeOffers(): void {
  // @ts-expect-error an end event has no successors
  ProcessPath.from(End).then((next) => next);
  // @ts-expect-error the end is no successor of the start
  ProcessPath.from(Start).then((next) => next.end);
  // @ts-expect-error the start is no subprocess
  ProcessPath.from(Start).enter((start) => start);
  // @ts-expect-error the start throws no compensation
  ProcessPath.from(Start).throwingCompensation(Boundary, (next) => next.handler);
  // @ts-expect-error jumping needs the opt-in
  ProcessPath.from(Start).jumpTo(End);
}
