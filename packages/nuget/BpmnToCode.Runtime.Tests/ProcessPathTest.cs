using System;
using System.Linq;
using Miragon.BpmnToCode.Runtime;
using Xunit;
using Api = MiraVelo.Process.BikeLeasingProcessApi;
using ApprovalApi = MiraVelo.Approval.ApprovalProcessApi;

namespace BpmnToCode.Runtime.Tests;

public class ProcessPathTest
{
    [Fact]
    public void HappyPathWalksThroughTheSubProcess()
    {
        // when
        var path = ProcessPath.From(Api.FlowNodes.StartEventLeasingRequestReceived)
            .Then(n => n.ServiceTaskValidateApplication)
            .Then(n => n.BusinessRuleTaskCheckCreditRating)
            .Then(n => n.GatewayIsSolvent)
            .Onto(n => n.SubProcessConcludeContract)
            .Inside(contract => contract
                .Enter(start => start.StartEventCustomerEligible)
                .Then(n => n.ServiceTaskSendContract)
                .Then(n => n.GatewayAwaitSignature)
                .Then(n => n.EventContractSigned)
                .End(n => n.EndEventContractConcluded))
            .Then(n => n.GatewayFork)
            .Then(n => n.ServiceTaskOrderBike)
            .Then(n => n.GatewayJoin)
            .Then(n => n.ReceiveTaskHandoverReported)
            .Then(n => n.TimerWithdrawalPeriodElapsed)
            .End(n => n.EndEventLeasingActive);

        // then
        Assert.Equal(
            new[]
            {
                "startEvent_leasingRequestReceived", "serviceTask_validateApplication", "businessRuleTask_checkCreditRating",
                "gateway_isSolvent", "startEvent_customerEligible", "serviceTask_sendContract", "gateway_awaitSignature",
                "event_contractSigned", "endEvent_contractConcluded", "gateway_fork", "serviceTask_orderBike", "gateway_join",
                "receiveTask_handoverReported", "timer_withdrawalPeriodElapsed", "endEvent_leasingActive",
            },
            path.Ids);
    }

    [Fact]
    public void EscalationLeavesTheSubProcessThroughItsBoundaryEvent()
    {
        // when
        var path = ProcessPath.From(Api.FlowNodes.StartEventLeasingRequestReceived)
            .Then(n => n.ServiceTaskValidateApplication)
            .Then(n => n.BusinessRuleTaskCheckCreditRating)
            .Enter(Api.FlowNodes.SubProcessConcludeContract, start => start.StartEventCustomerEligible)
            .Then(n => n.ServiceTaskSendContract)
            .Then(n => n.GatewayAwaitSignature)
            .Then(n => n.TimerSignatureDeadline)
            .End(n => n.EndEventContractNotSigned)
            .InterruptedBy(Api.FlowNodes.SubProcessConcludeContract, n => n.BoundaryContractNotSigned)
            .Then(n => n.GatewayCollectRejections)
            .Then(n => n.ServiceTaskSendRejection)
            .End(n => n.EndEventApplicationRejected);

        // then
        Assert.Equal(
            new[]
            {
                "startEvent_leasingRequestReceived", "serviceTask_validateApplication", "businessRuleTask_checkCreditRating",
                "startEvent_customerEligible", "serviceTask_sendContract", "gateway_awaitSignature", "timer_signatureDeadline",
                "endEvent_contractNotSigned", "boundary_contractNotSigned", "gateway_collectRejections",
                "serviceTask_sendRejection", "endEvent_applicationRejected",
            },
            path.Ids);
    }

    [Fact]
    public void ErrorBoundaryEventContinuesTheWalk()
    {
        // when
        var path = ProcessPath.From(Api.FlowNodes.StartEventLeasingRequestReceived)
            .Then(n => n.ServiceTaskValidateApplication)
            .Then(n => n.BoundaryApplicationInvalid)
            .Then(n => n.GatewayCollectRejections)
            .Then(n => n.ServiceTaskSendRejection)
            .End(n => n.EndEventApplicationRejected);

        // then
        Assert.Equal(
            new[]
            {
                "startEvent_leasingRequestReceived", "serviceTask_validateApplication", "boundary_applicationInvalid",
                "gateway_collectRejections", "serviceTask_sendRejection", "endEvent_applicationRejected",
            },
            path.Ids);
    }

    [Fact]
    public void NonInterruptingTimerBranchesOffTheSubProcess()
    {
        // when
        var path = ProcessPath.From(Api.FlowNodes.GatewayIsSolvent)
            .Onto(n => n.SubProcessConcludeContract)
            .Then(n => n.TimerSignatureReminder)
            .Then(n => n.ServiceTaskSendReminderMail)
            .End(n => n.EndEventCustomerReminded);

        // then
        Assert.Equal(
            new[] { "gateway_isSolvent", "timer_signatureReminder", "serviceTask_sendReminderMail", "endEvent_customerReminded" },
            path.Ids);
    }

    [Fact]
    public void WalkRecordsTheSequenceFlowsItSteppedAlong()
    {
        // when
        var path = ProcessPath.From(Api.FlowNodes.BusinessRuleTaskCheckCreditRating)
            .Then(n => n.GatewayIsSolvent)
            .Then(n => n.SubProcessConcludeContract);

        // then
        Assert.Equal(new[] { "businessRuleTask_checkCreditRating", "gateway_isSolvent", "subProcess_concludeContract" }, path.Ids);
        Assert.Equal(new[] { "flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract" }, path.FlowIds);
    }

    [Fact]
    public void ThrownCompensationVisitsTheHandlers()
    {
        // when
        var path = ProcessPath.From(Api.FlowNodes.StartEventApplicationWithdrawn)
            .Then(n => n.EventReverseApplication)
            .ThrowingCompensation(Api.FlowNodes.BoundaryCompensateContract, n => n.ServiceTaskCancelContract, includeBoundaryEvent: true)
            .ThrowingCompensation(Api.FlowNodes.BoundaryCompensateOrder, n => n.CallActivityCancelBikeOrder, includeBoundaryEvent: true)
            .Then(n => n.ServiceTaskSendCancellationConfirmation)
            .End(n => n.EndEventApplicationCancelled);

        // then
        Assert.Equal(
            new[]
            {
                "startEvent_applicationWithdrawn", "event_reverseApplication", "boundary_compensateContract",
                "serviceTask_cancelContract", "boundary_compensateOrder", "callActivity_cancelBikeOrder",
                "serviceTask_sendCancellationConfirmation", "endEvent_applicationCancelled",
            },
            path.Ids);
        Assert.Equal(
            new[]
            {
                "flow_applicationWithdrawnToReverseApplication", "flow_reverseApplicationToSendCancellationConfirmation",
                "flow_sendCancellationConfirmationToApplicationCancelled",
            },
            path.FlowIds);
    }

    [Fact]
    public void ThrownCompensationLeavesOutTheBoundaryEventByDefault()
    {
        // when
        var path = ProcessPath.From(Api.FlowNodes.StartEventApplicationWithdrawn)
            .Then(n => n.EventReverseApplication)
            .ThrowingCompensation(Api.FlowNodes.BoundaryCompensateInsurance, n => n.ServiceTaskCancelPolicy)
            .Then(n => n.ServiceTaskSendCancellationConfirmation);

        // then
        Assert.Equal(
            new[] { "startEvent_applicationWithdrawn", "event_reverseApplication", "serviceTask_cancelPolicy", "serviceTask_sendCancellationConfirmation" },
            path.Ids);
    }

    [Fact]
    public void ParallelBranchesShareTheirNodesOnce()
    {
        // given
        var orderBranch = ProcessPath.From(Api.FlowNodes.GatewayFork)
            .Then(n => n.ServiceTaskOrderBike).Then(n => n.GatewayJoin).Then(n => n.ReceiveTaskHandoverReported).Nodes;
        var insuranceBranch = ProcessPath.From(Api.FlowNodes.GatewayFork)
            .Then(n => n.ServiceTaskIssueInsurancePolicy).Then(n => n.GatewayJoin).Then(n => n.ReceiveTaskHandoverReported).Nodes;

        // when
        var ids = ProcessPath.NodesOf(orderBranch, insuranceBranch).Select(node => node.Id.Value).ToArray();

        // then
        Assert.Equal(ids.Distinct(), ids);
        Assert.Contains("serviceTask_orderBike", ids);
        Assert.Contains("serviceTask_issueInsurancePolicy", ids);
    }

    [Fact]
    public void JumpContinuesFromAnyNode()
    {
        // when
#pragma warning disable BPMNTOCODE001
        var path = ProcessPath.From(Api.FlowNodes.GatewayFork)
            .Then(n => n.ServiceTaskOrderBike)
            .JumpTo(Api.FlowNodes.GatewayFork)
            .Then(n => n.ServiceTaskIssueInsurancePolicy)
            .Then(n => n.GatewayJoin)
            .Then(n => n.ReceiveTaskHandoverReported);
#pragma warning restore BPMNTOCODE001

        // then
        Assert.Equal(
            new[] { "gateway_fork", "serviceTask_orderBike", "serviceTask_issueInsurancePolicy", "gateway_join", "receiveTask_handoverReported" },
            path.Ids);
    }

    [Fact]
    public void WalkRecordsThePickedOneOfSeveralFlowsToTheSameElement()
    {
        // when
        var path = ProcessPath.From(ApprovalApi.FlowNodes.GatewayAmount)
            .Then(n => n.TaskApprove.Flows[1])
            .End(n => n.EndEventApproved);

        // then
        Assert.Equal(new[] { "flow_vip", "flow_approved" }, path.FlowIds);
    }

    [Fact]
    public void SequenceFlowsWithoutAFlowAreRejected()
    {
        // when
        var create = () => new SequenceFlows<ApprovalApi.FlowNodes.TaskApproveNode>(
            ApprovalApi.FlowNodes.TaskApprove,
            Array.Empty<SequenceFlow<ApprovalApi.FlowNodes.TaskApproveNode>>());

        // then
        Assert.ThrowsAny<ArgumentException>(create);
    }

    [Fact]
    public void FlowNodesListsEveryNode()
    {
        // then
        Assert.NotEmpty(Api.FlowNodes.All);
    }
}
