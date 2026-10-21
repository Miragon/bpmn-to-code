import { BikeLeasingProcessApi } from "./generated/bike-leasing-and-cancel-bike-order/process/BikeLeasingProcessApi.js";
import { CancelBikeOrderProcessApi } from "./generated/bike-leasing-and-cancel-bike-order/process/CancelBikeOrderProcessApi.js";
import { Messages } from "./generated/bike-leasing-and-cancel-bike-order/process/Messages.js";
import { ProcessPath, type FlowNode, type VariableName } from "@miragon/bpmn-to-code-runtime";
import { ServiceTasks } from "./generated/bike-leasing-and-cancel-bike-order/process/ServiceTasks.js";

const nodes = BikeLeasingProcessApi.FlowNodes;
const processId: "bikeLeasing" = BikeLeasingProcessApi.PROCESS_ID;
const sendContract = nodes.ServiceTaskSendContract;
const jobType: typeof ServiceTasks.MIRAVELO_SEND_CONTRACT = sendContract.jobType;
const label: string | null = sendContract.name;
const contractId: VariableName<"OUTPUT"> = sendContract.variables.CONTRACT_ID;
const calledProcess: typeof CancelBikeOrderProcessApi.PROCESS_ID = nodes.CallActivityCancelBikeOrder.calledProcess;
const everyNode: readonly FlowNode[] = nodes.all;
const everyMessage: readonly string[] = Messages.all;

function workerFor(type: (typeof ServiceTasks)[Exclude<keyof typeof ServiceTasks, "all">]): string {
  return type;
}

// @ts-expect-error: an output is not an input
const notAnInput: VariableName<"INPUT"> = sendContract.variables.CONTRACT_ID;

const solvent = nodes.StartEventLeasingRequestReceived
  .next.serviceTaskValidateApplication.target
  .next.businessRuleTaskCheckCreditRating.target
  .next.gatewayIsSolvent.target;
const concluded = solvent.next.subProcessConcludeContract;
const approvedName: "Conclude contract" = concluded.target.name;
const isDefault: boolean = concluded.flow.isDefault;
const reminder: "attachedBoundaryEvent" = concluded.target.next.timerSignatureReminder.kind;
const host: typeof nodes.SubProcessConcludeContract = nodes.TimerSignatureReminder.attachedTo;

// @ts-expect-error: the gateway has no successor of that name
solvent.next.gatewayFork;

// @ts-expect-error: the chain skips the credit rating
nodes.StartEventLeasingRequestReceived.next.serviceTaskValidateApplication.target.next.gatewayIsSolvent;

const walked: string[] = ProcessPath.from(solvent).then((next) => next.subProcessConcludeContract).ids;

// @ts-expect-error: an end event has no successors
ProcessPath.from(nodes.EndEventLeasingActive).then((next) => next.gatewayFork);

// @ts-expect-error: a gateway has no interior to enter
ProcessPath.from(solvent).enter((start) => start.startEventCustomerEligible);

// @ts-expect-error: a gateway has no interior to walk
ProcessPath.from(solvent).inside((scope) => scope);

// @ts-expect-error: only an event that throws a compensation records one
ProcessPath.from(solvent).throwingCompensation(nodes.BoundaryCompensateContract, (next) => next.serviceTaskCancelContract);

// @ts-expect-error: the unchecked jump needs the explicit opt-in
ProcessPath.from(solvent).jumpTo(nodes.GatewayFork);

// @ts-expect-error: the reminder timer is a boundary event, not a compensation handler
ProcessPath.from(nodes.EventReverseApplication).throwingCompensation(nodes.SubProcessConcludeContract, (next) => next.timerSignatureReminder);

export const used = [
  processId,
  jobType,
  label,
  contractId,
  calledProcess,
  everyNode,
  everyMessage,
  workerFor(jobType),
  notAnInput,
  approvedName,
  isDefault,
  reminder,
  host,
  walked,
];
