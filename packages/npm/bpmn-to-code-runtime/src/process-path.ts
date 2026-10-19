import type {
  AssociatedCompensationHandler,
  CompensationThrowEvent,
  FlowNode,
  FlowScope,
  HasSuccessors,
  SequenceFlow,
  Successor,
} from "./types.js";

/** Pass to {@link ProcessPath.jumpTo} to opt into the one step that is not checked against the model. */
export const RiskyNavigation: unique symbol = Symbol("RiskyNavigation");

/**
 * A compile-checked walk over a generated `FlowNodes` navigation, standing on `current` and holding the nodes and
 * sequence flows it passed. The edge steps (`then`, `onto`) and the subprocess steps (`enter`, `inside`) only compile
 * for a real successor or inner start of the current node, so a model change breaks the build at the edge that moved.
 * `interruptedBy` and `throwingCompensation` check the picked successor but let the caller name its carrier;
 * `jumpTo` is the one unchecked step.
 *
 * @example
 * const path = ProcessPath.from(BikeLeasingProcessApi.FlowNodes.StartEventLeasingRequestReceived)
 *   .then((next) => next.serviceTaskValidateApplication)
 *   .then((next) => next.businessRuleTaskCheckCreditRating);
 * expect(passedElementIds).toEqual(path.ids);
 */
export class ProcessPath<N extends FlowNode> {
  readonly current: N;

  /** The recorded nodes, in walk order. */
  readonly nodes: readonly FlowNode[];

  /** The recorded sequence flows, in walk order. */
  readonly flows: readonly SequenceFlow[];

  private constructor(current: N, nodes: readonly FlowNode[], flows: readonly SequenceFlow[]) {
    this.current = current;
    this.nodes = nodes;
    this.flows = flows;
  }

  /** Starts a path at `start`, recording it as the first node. */
  static from<N extends FlowNode>(start: N): ProcessPath<N> {
    return new ProcessPath(start, [start], []);
  }

  /** Unites separately walked branches into one list without duplicates, for parallel branches whose order is not defined. */
  static nodesOf(...branches: readonly (readonly FlowNode[])[]): FlowNode[] {
    const byId = new Map<string, FlowNode>();
    branches.flat().forEach((node) => {
      if (!byId.has(node.id)) byId.set(node.id, node);
    });
    return [...byId.values()];
  }

  /** The element ids of the recorded nodes, in walk order. */
  get ids(): string[] {
    return this.nodes.map((node) => node.id);
  }

  /** The element ids of the recorded nodes, each once. */
  get distinctIds(): string[] {
    return [...new Set(this.ids)];
  }

  /** The ids of the recorded sequence flows, in walk order. */
  get flowIds(): string[] {
    return this.flows.map((flow) => flow.id);
  }

  /**
   * Advances to a successor of the current node and records it. `pick` gets the node's `next`, so only a real successor
   * compiles. The sequence flow is recorded too when it is unambiguous: a picked flow, or sequence flows with exactly one.
   */
  then<NEXT, M extends FlowNode>(this: ProcessPath<HasSuccessors<NEXT>>, pick: (next: NEXT) => Successor<M>): ProcessPath<M> {
    return this.traverse(pick(this.current.next), 1);
  }

  /**
   * Records the same successor `repeatTimes` times in a row, for a sequential multi-instance activity or a consecutive
   * self-repeat. The flow leading there is recorded once, as the engine takes it once.
   */
  thenMultipleTimes<NEXT, M extends FlowNode>(
    this: ProcessPath<HasSuccessors<NEXT>>,
    repeatTimes: number,
    pick: (next: NEXT) => Successor<M>,
  ): ProcessPath<M> {
    return this.traverse(pick(this.current.next), repeatTimes);
  }

  /**
   * Moves onto a subprocess successor without recording it, ready to descend with `enter` or to walk it with `inside`.
   * A subprocess brackets its interior rather than being a point in the ordered flow; the flow into it is recorded.
   */
  onto<NEXT, M extends FlowNode>(this: ProcessPath<HasSuccessors<NEXT>>, subprocess: (next: NEXT) => Successor<M>): ProcessPath<M> {
    const successor = subprocess(this.current.next);
    return this.moveTo(successor.target, [], takenFlowOf(successor));
  }

  /**
   * Descends into the current subprocess and records the picked inner start; `pick` gets its `startEvents`. With a
   * `scope`, descends into that subprocess from any position. Leave the interior through `inside` or `interruptedBy`.
   */
  enter<START, M extends FlowNode>(this: ProcessPath<FlowScope<START>>, pick: (start: START) => M): ProcessPath<M>;
  enter<START, M extends FlowNode>(scope: FlowScope<START>, pick: (start: START) => M): ProcessPath<M>;
  enter<START, M extends FlowNode>(
    scopeOrPick: FlowScope<START> | ((start: START) => M),
    pickInScope?: (start: START) => M,
  ): ProcessPath<M> {
    const scope = typeof scopeOrPick === "function" ? (this.current as unknown as FlowScope<START>) : scopeOrPick;
    const pick = typeof scopeOrPick === "function" ? scopeOrPick : pickInScope;
    if (pick === undefined) {
      throw new Error("enter needs a pick of the inner start");
    }
    const node = pick(scope.startEvents);
    return this.moveTo(node, [node], []);
  }

  /**
   * Walks the interior of the current subprocess in `walk`, which begins on the subprocess and opens it with `enter`,
   * then stays on the subprocess, so the next `then` is a checked step after it. The walked nodes and flows are recorded.
   */
  inside<S extends FlowScope>(this: ProcessPath<S>, walk: (subprocess: ProcessPath<S>) => ProcessPath<FlowNode>): ProcessPath<S> {
    const interior = walk(new ProcessPath(this.current, [], []));
    return this.moveTo(this.current, interior.nodes, interior.flows);
  }

  /**
   * Leaves an activity or subprocess through one of its boundary events: re-anchors to `carrier` and records the picked
   * successor, which only one of the carrier's successors can be.
   */
  interruptedBy<NEXT, M extends FlowNode>(carrier: HasSuccessors<NEXT>, pick: (next: NEXT) => Successor<M>): ProcessPath<M> {
    return this.traverse(pick(carrier.next), 1);
  }

  /**
   * Records a compensation the current event throws: the handler associated with the compensation `boundaryEvent` of
   * the compensated activity. Only a compensation throw event can do this, and only a compensation boundary event offers
   * a handler. The path stays on the current event. The boundary event is left out unless `includeBoundaryEvent` is set,
   * as only Zeebe reports it as passed.
   */
  throwingCompensation<C extends CompensationThrowEvent, NEXT, H extends FlowNode>(
    this: ProcessPath<C>,
    boundaryEvent: HasSuccessors<NEXT>,
    pick: (next: NEXT) => AssociatedCompensationHandler<H>,
    options: { readonly includeBoundaryEvent?: boolean } = {},
  ): ProcessPath<C> {
    const handler = pick(boundaryEvent.next).target;
    const recorded = options.includeBoundaryEvent === true ? [boundaryEvent, handler] : [handler];
    return this.moveTo(this.current, recorded, []);
  }

  /**
   * Re-anchors to any node without recording it and without checking that it can follow: the escape hatch, for example
   * to step back to a parallel fork. Pass {@link RiskyNavigation}, so every use is an explicit opt-in.
   */
  jumpTo<M extends FlowNode>(node: M, optIn: typeof RiskyNavigation): ProcessPath<M> {
    if (optIn !== RiskyNavigation) {
      throw new Error("jumpTo needs the RiskyNavigation opt-in");
    }
    return this.moveTo(node, [], []);
  }

  private traverse<M extends FlowNode>(successor: Successor<M>, repeatTimes: number): ProcessPath<M> {
    const recorded = Array.from({ length: repeatTimes }, () => successor.target);
    return this.moveTo(successor.target, recorded, takenFlowOf(successor));
  }

  private moveTo<M extends FlowNode>(node: M, nodes: readonly FlowNode[], flows: readonly SequenceFlow[]): ProcessPath<M> {
    return new ProcessPath(node, [...this.nodes, ...nodes], [...this.flows, ...flows]);
  }
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
