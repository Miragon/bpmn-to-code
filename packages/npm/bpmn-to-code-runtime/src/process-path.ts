import type {
  AssociatedCompensationHandler,
  CompensationThrowEvent,
  FlowNode,
  FlowScope,
  HasSuccessors,
  SequenceFlow,
  Successor,
} from "./types.js";

/**
 * Pass to ProcessPath.jumpTo to opt into the one step that is not checked against the model.
 */
export const RiskyNavigation: unique symbol = Symbol("RiskyNavigation");

/**
 * A compile-checked walk over FlowNodes that records the elements and sequence flows it passes, for example
 * ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived).then((next) => next.serviceTaskValidateApplication).
 * Feed ids to the flow assertion of the engine, for example hasPassedInOrder(...path.ids).
 */
export class ProcessPath<NODE extends FlowNode> {
  private constructor(
    readonly current: NODE,
    readonly nodes: readonly FlowNode[],
    private readonly flows: readonly SequenceFlow[],
  ) {}

  static from<NODE extends FlowNode>(start: NODE): ProcessPath<NODE> {
    return new ProcessPath(start, [start], []);
  }

  get ids(): string[] {
    return this.nodes.map((node) => node.id);
  }

  get distinctIds(): string[] {
    return [...new Set(this.ids)];
  }

  /**
   * The sequence flows walked, as far as a step names one flow; boundary events, compensation handlers and entering a
   * subprocess walk none.
   */
  get flowIds(): string[] {
    return this.flows.map((flow) => flow.id);
  }

  then<NEXT, TARGET extends FlowNode>(this: ProcessPath<HasSuccessors<NEXT>>, pick: (next: NEXT) => Successor<TARGET>): ProcessPath<TARGET> {
    return this.traverse(pick(this.current.next), 1);
  }

  /**
   * Records the same successor several times in a row, for a sequential multi-instance activity.
   */
  thenMultipleTimes<NEXT, TARGET extends FlowNode>(
    this: ProcessPath<HasSuccessors<NEXT>>,
    times: number,
    pick: (next: NEXT) => Successor<TARGET>,
  ): ProcessPath<TARGET> {
    return this.traverse(pick(this.current.next), times);
  }

  /**
   * Moves onto a successor without recording it, typically a subprocess to walk with enter or inside.
   */
  onto<NEXT, TARGET extends FlowNode>(this: ProcessPath<HasSuccessors<NEXT>>, pick: (next: NEXT) => Successor<TARGET>): ProcessPath<TARGET> {
    const successor = pick(this.current.next);
    return this.moveTo(successor.target, [], takenFlowOf(successor));
  }

  /**
   * Enters the current subprocess, or the one passed as scope, and records the start element picked from its interior.
   */
  enter<START, TARGET extends FlowNode>(this: ProcessPath<FlowScope<START>>, pick: (start: START) => TARGET): ProcessPath<TARGET>;
  enter<START, TARGET extends FlowNode>(scope: FlowScope<START>, pick: (start: START) => TARGET): ProcessPath<TARGET>;
  enter<START, TARGET extends FlowNode>(
    scopeOrPick: FlowScope<START> | ((start: START) => TARGET),
    pickFromScope?: (start: START) => TARGET,
  ): ProcessPath<TARGET> {
    const scope = pickFromScope === undefined ? (this.current as unknown as FlowScope<START>) : (scopeOrPick as FlowScope<START>);
    const pick = pickFromScope ?? (scopeOrPick as (start: START) => TARGET);
    const start = pick(scope.startEvents);
    return this.moveTo(start, [start], []);
  }

  /**
   * Walks the interior of the current subprocess in block and continues on the subprocess, so the next step leaves it.
   */
  inside<SCOPE extends FlowScope>(this: ProcessPath<SCOPE>, block: (scope: ProcessPath<SCOPE>) => ProcessPath<FlowNode>): ProcessPath<SCOPE> {
    const interior = block(new ProcessPath(this.current, [], []));
    return this.moveTo(this.current, interior.nodes, interior.flows);
  }

  /**
   * Leaves through a boundary event of carrier and records it.
   */
  interruptedBy<NEXT, TARGET extends FlowNode>(carrier: HasSuccessors<NEXT>, pick: (next: NEXT) => Successor<TARGET>): ProcessPath<TARGET> {
    return this.traverse(pick(carrier.next), 1);
  }

  /**
   * Records the compensation handler the current event triggers through a compensation boundary event and stays on
   * the event. Only Zeebe reports the boundary event as passed; pass includeBoundaryEvent to record it as well.
   */
  throwingCompensation<THROWER extends CompensationThrowEvent, NEXT, HANDLER extends FlowNode>(
    this: ProcessPath<THROWER>,
    boundaryEvent: HasSuccessors<NEXT>,
    pick: (next: NEXT) => AssociatedCompensationHandler<HANDLER>,
    options: { readonly includeBoundaryEvent?: boolean } = {},
  ): ProcessPath<THROWER> {
    const handler = pick(boundaryEvent.next).target;
    const recorded = options.includeBoundaryEvent === true ? [boundaryEvent, handler] : [handler];
    return this.moveTo(this.current, recorded, []);
  }

  /**
   * Moves to any node without recording it and without checking that the model leads there. Pass RiskyNavigation to
   * opt in, so the one unchecked step stands out in review.
   */
  jumpTo<TARGET extends FlowNode>(optIn: typeof RiskyNavigation, node: TARGET): ProcessPath<TARGET> {
    if (optIn !== RiskyNavigation) {
      throw new Error("jumpTo needs RiskyNavigation");
    }
    return this.moveTo(node, [], []);
  }

  private traverse<TARGET extends FlowNode>(successor: Successor<TARGET>, times: number): ProcessPath<TARGET> {
    const targets = Array.from({ length: times }, () => successor.target);
    return this.moveTo(successor.target, targets, takenFlowOf(successor));
  }

  private moveTo<TARGET extends FlowNode>(node: TARGET, nodes: readonly FlowNode[], flows: readonly SequenceFlow[]): ProcessPath<TARGET> {
    return new ProcessPath(node, [...this.nodes, ...nodes], [...this.flows, ...flows]);
  }
}

/**
 * Unites separately walked parallel branches into one set without order and duplicates.
 */
export function nodesOf(...branches: (readonly FlowNode[])[]): FlowNode[] {
  return [...new Set(branches.flat())];
}

function takenFlowOf(successor: Successor): SequenceFlow[] {
  switch (successor.kind) {
    case "sequenceFlow":
      return [successor];
    case "sequenceFlows":
      return successor.flows.length === 1 ? [...successor.flows] : [];
    default:
      return [];
  }
}
