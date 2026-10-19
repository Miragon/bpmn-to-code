import assert from "node:assert/strict";
import { test } from "node:test";
import { ProcessPath, RiskyNavigation } from "@miragon/bpmn-to-code-runtime";
import { BikeLeasingProcessApi } from "./example/BikeLeasingProcessApi.js";
import { Errors } from "./example/Errors.js";
import { Messages } from "./example/Messages.js";
import { ProcessVariables } from "./example/ProcessVariables.js";

/*
 * ProcessPath over the generated TypeScript bike-leasing API in example/, which core's drift test keeps equal to the
 * generator's output. Compiling this test is the contract that the generated nodes satisfy the runtime interfaces the
 * steps are typed with.
 */
const BikeLeasing = BikeLeasingProcessApi.FlowNodes;

test("happy path walks the subprocess interior with inside and continues checked after it", () => {
  const path = ProcessPath.from(BikeLeasing.StartEventLeasingRequestReceived)
    .then((next) => next.serviceTaskValidateApplication)
    .then((next) => next.businessRuleTaskCheckCreditRating)
    .then((next) => next.gatewayIsSolvent)
    .onto((next) => next.subProcessConcludeContract)
    .inside((contract) =>
      contract
        .enter((start) => start.startEventCustomerEligible)
        .then((next) => next.serviceTaskSendContract)
        .then((next) => next.gatewayAwaitSignature)
        .then((next) => next.eventContractSigned)
        .then((next) => next.endEventContractConcluded),
    )
    .then((next) => next.gatewayFork)
    .then((next) => next.serviceTaskOrderBike)
    .then((next) => next.gatewayJoin)
    .then((next) => next.receiveTaskHandoverReported)
    .then((next) => next.timerWithdrawalPeriodElapsed)
    .then((next) => next.endEventLeasingActive);

  assert.deepEqual(path.ids, [
    "startEvent_leasingRequestReceived",
    "serviceTask_validateApplication",
    "businessRuleTask_checkCreditRating",
    "gateway_isSolvent",
    "startEvent_customerEligible",
    "serviceTask_sendContract",
    "gateway_awaitSignature",
    "event_contractSigned",
    "endEvent_contractConcluded",
    "gateway_fork",
    "serviceTask_orderBike",
    "gateway_join",
    "receiveTask_handoverReported",
    "timer_withdrawalPeriodElapsed",
    "endEvent_leasingActive",
  ]);
});

test("escalation boundary leaves the subprocess entered through an explicit scope", () => {
  const path = ProcessPath.from(BikeLeasing.StartEventLeasingRequestReceived)
    .then((next) => next.serviceTaskValidateApplication)
    .then((next) => next.businessRuleTaskCheckCreditRating)
    .enter(BikeLeasing.SubProcessConcludeContract, (start) => start.startEventCustomerEligible)
    .then((next) => next.serviceTaskSendContract)
    .then((next) => next.gatewayAwaitSignature)
    .then((next) => next.timerSignatureDeadline)
    .then((next) => next.endEventContractNotSigned)
    .interruptedBy(BikeLeasing.SubProcessConcludeContract, (next) => next.boundaryContractNotSigned)
    .then((next) => next.gatewayCollectRejections)
    .then((next) => next.serviceTaskSendRejection)
    .then((next) => next.endEventApplicationRejected);

  assert.deepEqual(path.ids, [
    "startEvent_leasingRequestReceived",
    "serviceTask_validateApplication",
    "businessRuleTask_checkCreditRating",
    "startEvent_customerEligible",
    "serviceTask_sendContract",
    "gateway_awaitSignature",
    "timer_signatureDeadline",
    "endEvent_contractNotSigned",
    "boundary_contractNotSigned",
    "gateway_collectRejections",
    "serviceTask_sendRejection",
    "endEvent_applicationRejected",
  ]);
});

test("non-interrupting timer boundary branches off the subprocess", () => {
  const path = ProcessPath.from(BikeLeasing.GatewayIsSolvent)
    .onto((next) => next.subProcessConcludeContract)
    .then((next) => next.timerSignatureReminder)
    .then((next) => next.serviceTaskSendReminderMail)
    .then((next) => next.endEventCustomerReminded);

  assert.deepEqual(path.ids, ["gateway_isSolvent", "timer_signatureReminder", "serviceTask_sendReminderMail", "endEvent_customerReminded"]);
});

test("parallel branches unite into a list without duplicates", () => {
  const orderBranch = ProcessPath.from(BikeLeasing.GatewayFork).then((next) => next.serviceTaskOrderBike).then((next) => next.gatewayJoin).nodes;
  const insuranceBranch = ProcessPath.from(BikeLeasing.GatewayFork).then((next) => next.serviceTaskIssueInsurancePolicy).then((next) => next.gatewayJoin).nodes;

  assert.deepEqual(ProcessPath.nodesOf(orderBranch, insuranceBranch).map((node) => node.id), [
    "gateway_fork",
    "serviceTask_orderBike",
    "gateway_join",
    "serviceTask_issueInsurancePolicy",
  ]);
});

test("then records the sequence flows it walks", () => {
  const path = ProcessPath.from(BikeLeasing.BusinessRuleTaskCheckCreditRating)
    .then((next) => next.gatewayIsSolvent)
    .then((next) => next.subProcessConcludeContract);

  assert.deepEqual(path.flowIds, ["flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract"]);
  assert.equal(BikeLeasing.GatewayIsSolvent.next.subProcessConcludeContract.flow.isDefault, true);
});

test("compensation records each handler after the event that throws it", () => {
  const path = ProcessPath.from(BikeLeasing.StartEventApplicationWithdrawn)
    .then((next) => next.eventReverseApplication)
    .throwingCompensation(BikeLeasing.BoundaryCompensateContract, (next) => next.serviceTaskCancelContract, { includeBoundaryEvent: true })
    .throwingCompensation(BikeLeasing.BoundaryCompensateOrder, (next) => next.callActivityCancelBikeOrder)
    .then((next) => next.serviceTaskSendCancellationConfirmation)
    .then((next) => next.endEventApplicationCancelled);

  assert.deepEqual(path.ids, [
    "startEvent_applicationWithdrawn",
    "event_reverseApplication",
    "boundary_compensateContract",
    "serviceTask_cancelContract",
    "callActivity_cancelBikeOrder",
    "serviceTask_sendCancellationConfirmation",
    "endEvent_applicationCancelled",
  ]);
});

test("jumpTo re-anchors to the fork to walk the second branch in one chain", () => {
  const path = ProcessPath.from(BikeLeasing.GatewayFork)
    .then((next) => next.serviceTaskOrderBike)
    .jumpTo(BikeLeasing.GatewayFork, RiskyNavigation)
    .then((next) => next.serviceTaskIssueInsurancePolicy);

  assert.deepEqual(path.ids, ["gateway_fork", "serviceTask_orderBike", "serviceTask_issueInsurancePolicy"]);
});

test("nodes expose their metadata and facets through the runtime types", () => {
  assert.equal(BikeLeasing.StartEventLeasingRequestReceived.elementType, "START_EVENT");
  assert.equal(BikeLeasing.StartEventLeasingRequestReceived.eventType, "MESSAGE");
  assert.equal(BikeLeasing.StartEventLeasingRequestReceived.message, Messages.MIRAVELO_LEASING_REQUEST_RECEIVED);
  assert.deepEqual(BikeLeasing.BoundaryApplicationInvalid.error, Errors.MIRAVELO_APPLICATION_INVALID);
  assert.deepEqual(BikeLeasing.TimerSignatureReminder.timer, { type: "DURATION", timerValue: "P7D" });
  assert.equal(BikeLeasing.TimerSignatureReminder.attachedTo, BikeLeasing.SubProcessConcludeContract);
  assert.equal(BikeLeasing.TimerSignatureReminder.isInterrupting, false);
  assert.equal(BikeLeasing.CallActivityCancelBikeOrder.calledProcess, "cancelBikeOrder");
  assert.equal(BikeLeasing.CallActivityCancelBikeOrder.Inputs.ORDER_IDS.target, "orderIds");
  assert.equal(BikeLeasing.ServiceTaskSendContract.variables.APPLICATION_ID.value, ProcessVariables.APPLICATION_ID);
  assert.equal(new Set(BikeLeasing.all.map((node) => node.id)).size, BikeLeasing.all.length);
});
