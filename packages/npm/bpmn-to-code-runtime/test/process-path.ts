import { BikeLeasingProcessApi } from "./generated/zeebe-bike-leasing/process/BikeLeasingProcessApi.js";
import { nodesOf, ProcessPath, RiskyNavigation } from "@miragon/bpmn-to-code-runtime";

const nodes = BikeLeasingProcessApi.FlowNodes;

const happyPath = ProcessPath.from(nodes.StartEventLeasingRequestReceived)
  .then((next) => next.serviceTaskValidateApplication)
  .then((next) => next.businessRuleTaskCheckCreditRating)
  .then((next) => next.gatewayIsSolvent)
  .onto((next) => next.subProcessConcludeContract)
  .inside((contract) => contract
    .enter((start) => start.startEventCustomerEligible)
    .then((next) => next.serviceTaskSendContract)
    .then((next) => next.gatewayAwaitSignature)
    .then((next) => next.eventContractSigned)
    .then((next) => next.endEventContractConcluded))
  .then((next) => next.gatewayFork)
  .then((next) => next.serviceTaskOrderBike)
  .then((next) => next.gatewayJoin)
  .then((next) => next.receiveTaskHandoverReported)
  .then((next) => next.timerWithdrawalPeriodElapsed)
  .then((next) => next.endEventLeasingActive);
expect("happy path", happyPath.ids, ["startEvent_leasingRequestReceived", "serviceTask_validateApplication", "businessRuleTask_checkCreditRating", "gateway_isSolvent", "startEvent_customerEligible", "serviceTask_sendContract", "gateway_awaitSignature", "event_contractSigned", "endEvent_contractConcluded", "gateway_fork", "serviceTask_orderBike", "gateway_join", "receiveTask_handoverReported", "timer_withdrawalPeriodElapsed", "endEvent_leasingActive"]);

const escalation = ProcessPath.from(nodes.StartEventLeasingRequestReceived)
  .then((next) => next.serviceTaskValidateApplication)
  .then((next) => next.businessRuleTaskCheckCreditRating)
  .enter(nodes.SubProcessConcludeContract, (start) => start.startEventCustomerEligible)
  .then((next) => next.serviceTaskSendContract)
  .then((next) => next.gatewayAwaitSignature)
  .then((next) => next.timerSignatureDeadline)
  .then((next) => next.endEventContractNotSigned)
  .interruptedBy(nodes.SubProcessConcludeContract, (next) => next.boundaryContractNotSigned)
  .then((next) => next.gatewayCollectRejections)
  .then((next) => next.serviceTaskSendRejection)
  .then((next) => next.endEventApplicationRejected);
expect("escalation", escalation.ids, ["startEvent_leasingRequestReceived", "serviceTask_validateApplication", "businessRuleTask_checkCreditRating", "startEvent_customerEligible", "serviceTask_sendContract", "gateway_awaitSignature", "timer_signatureDeadline", "endEvent_contractNotSigned", "boundary_contractNotSigned", "gateway_collectRejections", "serviceTask_sendRejection", "endEvent_applicationRejected"]);

const errorBoundary = ProcessPath.from(nodes.StartEventLeasingRequestReceived)
  .then((next) => next.serviceTaskValidateApplication)
  .then((next) => next.boundaryApplicationInvalid)
  .then((next) => next.gatewayCollectRejections)
  .then((next) => next.serviceTaskSendRejection)
  .then((next) => next.endEventApplicationRejected);
expect("error boundary", errorBoundary.ids, ["startEvent_leasingRequestReceived", "serviceTask_validateApplication", "boundary_applicationInvalid", "gateway_collectRejections", "serviceTask_sendRejection", "endEvent_applicationRejected"]);

const reminder = ProcessPath.from(nodes.GatewayIsSolvent)
  .onto((next) => next.subProcessConcludeContract)
  .then((next) => next.timerSignatureReminder)
  .then((next) => next.serviceTaskSendReminderMail)
  .then((next) => next.endEventCustomerReminded);
expect("reminder", reminder.ids, ["gateway_isSolvent", "timer_signatureReminder", "serviceTask_sendReminderMail", "endEvent_customerReminded"]);

const flows = ProcessPath.from(nodes.BusinessRuleTaskCheckCreditRating)
  .then((next) => next.gatewayIsSolvent)
  .then((next) => next.subProcessConcludeContract);
expect("flow ids", flows.ids, ["businessRuleTask_checkCreditRating", "gateway_isSolvent", "subProcess_concludeContract"]);
expect("flows", flows.flowIds, ["flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract"]);

const compensated = ProcessPath.from(nodes.StartEventApplicationWithdrawn)
  .then((next) => next.eventReverseApplication)
  .throwingCompensation(nodes.BoundaryCompensateContract, (next) => next.serviceTaskCancelContract, { includeBoundaryEvent: true })
  .throwingCompensation(nodes.BoundaryCompensateOrder, (next) => next.callActivityCancelBikeOrder, { includeBoundaryEvent: true })
  .then((next) => next.serviceTaskSendCancellationConfirmation)
  .then((next) => next.endEventApplicationCancelled);
expect("compensation", compensated.ids, ["startEvent_applicationWithdrawn", "event_reverseApplication", "boundary_compensateContract", "serviceTask_cancelContract", "boundary_compensateOrder", "callActivity_cancelBikeOrder", "serviceTask_sendCancellationConfirmation", "endEvent_applicationCancelled"]);
expect("compensation flows", compensated.flowIds, ["flow_applicationWithdrawnToReverseApplication", "flow_reverseApplicationToSendCancellationConfirmation", "flow_sendCancellationConfirmationToApplicationCancelled"]);

const withoutBoundary = ProcessPath.from(nodes.StartEventApplicationWithdrawn)
  .then((next) => next.eventReverseApplication)
  .throwingCompensation(nodes.BoundaryCompensateInsurance, (next) => next.serviceTaskCancelPolicy)
  .then((next) => next.serviceTaskSendCancellationConfirmation);
expect("compensation without boundary", withoutBoundary.ids, ["startEvent_applicationWithdrawn", "event_reverseApplication", "serviceTask_cancelPolicy", "serviceTask_sendCancellationConfirmation"]);

const orderBranch = ProcessPath.from(nodes.GatewayFork)
  .then((next) => next.serviceTaskOrderBike)
  .then((next) => next.gatewayJoin)
  .then((next) => next.receiveTaskHandoverReported).nodes;
const insuranceBranch = ProcessPath.from(nodes.GatewayFork)
  .then((next) => next.serviceTaskIssueInsurancePolicy)
  .then((next) => next.gatewayJoin)
  .then((next) => next.receiveTaskHandoverReported).nodes;
const parallel = nodesOf(orderBranch, insuranceBranch).map((node) => node.id);
if (new Set(parallel).size !== parallel.length || !parallel.includes("serviceTask_orderBike") || !parallel.includes("serviceTask_issueInsurancePolicy")) {
  throw new Error("parallel branches");
}

const jumped = ProcessPath.from(nodes.GatewayFork)
  .then((next) => next.serviceTaskOrderBike)
  .jumpTo(RiskyNavigation, nodes.GatewayFork)
  .then((next) => next.serviceTaskIssueInsurancePolicy)
  .then((next) => next.gatewayJoin)
  .then((next) => next.receiveTaskHandoverReported);
expect("jump", jumped.ids, ["gateway_fork", "serviceTask_orderBike", "serviceTask_issueInsurancePolicy", "gateway_join", "receiveTask_handoverReported"]);

const repeated = ProcessPath.from(nodes.GatewayFork).thenMultipleTimes(3, (next) => next.serviceTaskOrderBike);
expect("repeated", repeated.ids, ["gateway_fork", "serviceTask_orderBike", "serviceTask_orderBike", "serviceTask_orderBike"]);
expect("repeated distinct", repeated.distinctIds, ["gateway_fork", "serviceTask_orderBike"]);

const picked = ProcessPath.from(nodes.GatewayIsSolvent).then((next) => next.subProcessConcludeContract.flow);
expect("picked flow", picked.flowIds, ["flow_isSolventToConcludeContract"]);

console.log("all process path scenarios passed");

function expect(scenario: string, actual: readonly string[], expected: readonly string[]): void {
  if (actual.join() !== expected.join()) {
    throw new Error(`${scenario}: expected [${expected.join(", ")}] but walked [${actual.join(", ")}]`);
  }
}
