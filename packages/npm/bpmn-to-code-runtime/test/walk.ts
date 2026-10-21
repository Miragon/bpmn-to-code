import { BikeLeasingProcessApi } from "./generated/zeebe-bike-leasing/process/BikeLeasingProcessApi.js";
import { NestedSubprocessProcessProcessApi } from "./generated/nested-subprocess/process/NestedSubprocessProcessProcessApi.js";
import type { FlowNode } from "@miragon/bpmn-to-code-runtime";

type Reached = { kind: string; target: FlowNode; flows?: readonly { kind: string; target: FlowNode }[] };
type Navigable = { next?: Record<string, Reached>; startEvents?: Record<string, FlowNode>; attachedTo?: FlowNode };

const apis = { BikeLeasingProcessApi, NestedSubprocessProcessProcessApi };

for (const [apiName, api] of Object.entries(apis)) {
  const checked = walk(api.FlowNodes as unknown as Record<string, FlowNode>, new Set(api.FlowNodes.all));
  console.log(`${apiName}: checked ${checked} successors and start elements`);
}

function walk(nodes: Record<string, FlowNode>, everyNode: Set<FlowNode>): number {
  let checked = 0;
  for (const [key, node] of Object.entries(nodes)) {
    if (key === "all") continue;
    if (node.constructor.name !== key || !everyNode.has(node)) throw new Error(`FlowNodes.${key} is not its node`);
    const { next, startEvents, attachedTo } = node as Navigable;
    for (const [name, successor] of Object.entries(next ?? {})) {
      if (!everyNode.has(successor.target)) throw new Error(`${key}.next.${name} leads outside FlowNodes`);
      if (successor.flows?.some((flow) => flow.kind !== "sequenceFlow" || flow.target !== successor.target)) {
        throw new Error(`${key}.next.${name}: a flow leads elsewhere`);
      }
      checked++;
    }
    for (const [name, start] of Object.entries(startEvents ?? {})) {
      if (!everyNode.has(start)) throw new Error(`${key}.startEvents.${name} is not in FlowNodes`);
      checked++;
    }
    if (attachedTo !== undefined && !everyNode.has(attachedTo)) throw new Error(`${key}.attachedTo is not in FlowNodes`);
  }
  if (everyNode.size !== Object.keys(nodes).length - 1) throw new Error("all lists other nodes than FlowNodes");
  return checked;
}
