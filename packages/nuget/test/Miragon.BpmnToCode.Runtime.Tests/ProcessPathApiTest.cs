using System.Linq;
using Miragon.BpmnToCode.Runtime.Path;
using Miragon.BpmnToCode.Runtime.Tests.Example;
using Xunit;
using BikeLeasing = Miragon.BpmnToCode.Runtime.Tests.Example.BikeLeasingProcessApi.FlowNodes;

namespace Miragon.BpmnToCode.Runtime.Tests;

/// <summary>
/// <see cref="ProcessPath"/> over the generated C# bike-leasing API in <c>Example/</c>, which core's drift test keeps equal
/// to the generator's output. Compiling this test is the contract that the generated nodes implement the runtime
/// interfaces the steps are typed with; bike leasing covers subprocesses, boundary events, parallel branches and
/// compensation.
/// </summary>
public class ProcessPathApiTest
{
    [Fact]
    public void Happy_path_walks_the_subprocess_interior_with_Inside_and_continues_checked_after_it()
    {
        var path = ProcessPath.From(BikeLeasing.StartEventLeasingRequestReceived.Instance)
            .Then(next => next.ServiceTaskValidateApplication)
            .Then(next => next.BusinessRuleTaskCheckCreditRating)
            .Then(next => next.GatewayIsSolvent)
            .Onto(next => next.SubProcessConcludeContract)
            .Inside(contract => contract
                .Enter(start => start.StartEventCustomerEligible)
                .Then(next => next.ServiceTaskSendContract)
                .Then(next => next.GatewayAwaitSignature)
                .Then(next => next.EventContractSigned)
                .Then(next => next.EndEventContractConcluded))
            .Then(next => next.GatewayFork)
            .Then(next => next.ServiceTaskOrderBike)
            .Then(next => next.GatewayJoin)
            .Then(next => next.ReceiveTaskHandoverReported)
            .Then(next => next.TimerWithdrawalPeriodElapsed)
            .Then(next => next.EndEventLeasingActive);

        Assert.Equal(
            new[]
            {
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
            },
            path.Ids);
    }

    [Fact]
    public void Escalation_boundary_leaves_the_subprocess_entered_through_an_explicit_scope()
    {
        var path = ProcessPath.From(BikeLeasing.StartEventLeasingRequestReceived.Instance)
            .Then(next => next.ServiceTaskValidateApplication)
            .Then(next => next.BusinessRuleTaskCheckCreditRating)
            .Enter(BikeLeasing.SubProcessConcludeContract.Instance, start => start.StartEventCustomerEligible)
            .Then(next => next.ServiceTaskSendContract)
            .Then(next => next.GatewayAwaitSignature)
            .Then(next => next.TimerSignatureDeadline)
            .Then(next => next.EndEventContractNotSigned)
            .InterruptedBy(BikeLeasing.SubProcessConcludeContract.Instance, next => next.BoundaryContractNotSigned)
            .Then(next => next.GatewayCollectRejections)
            .Then(next => next.ServiceTaskSendRejection)
            .Then(next => next.EndEventApplicationRejected);

        Assert.Equal(
            new[]
            {
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
            },
            path.Ids);
    }

    [Fact]
    public void Non_interrupting_timer_boundary_branches_off_the_subprocess()
    {
        var path = ProcessPath.From(BikeLeasing.GatewayIsSolvent.Instance)
            .Onto(next => next.SubProcessConcludeContract)
            .Then(next => next.TimerSignatureReminder)
            .Then(next => next.ServiceTaskSendReminderMail)
            .Then(next => next.EndEventCustomerReminded);

        Assert.Equal(new[] { "gateway_isSolvent", "timer_signatureReminder", "serviceTask_sendReminderMail", "endEvent_customerReminded" }, path.Ids);
    }

    [Fact]
    public void Parallel_branches_unite_into_a_set_without_duplicates()
    {
        var orderBranch = ProcessPath.From(BikeLeasing.GatewayFork.Instance)
            .Then(next => next.ServiceTaskOrderBike).Then(next => next.GatewayJoin).Nodes;
        var insuranceBranch = ProcessPath.From(BikeLeasing.GatewayFork.Instance)
            .Then(next => next.ServiceTaskIssueInsurancePolicy).Then(next => next.GatewayJoin).Nodes;

        var passed = ProcessPath.NodesOf(orderBranch, insuranceBranch).Select(node => node.Id.Value);

        Assert.Equal(new[] { "gateway_fork", "serviceTask_orderBike", "gateway_join", "serviceTask_issueInsurancePolicy" }, passed);
    }

    [Fact]
    public void Then_records_the_sequence_flows_it_walks()
    {
        var path = ProcessPath.From(BikeLeasing.BusinessRuleTaskCheckCreditRating.Instance)
            .Then(next => next.GatewayIsSolvent)
            .Then(next => next.SubProcessConcludeContract);

        Assert.Equal(new[] { "flow_checkCreditRatingToIsSolvent", "flow_isSolventToConcludeContract" }, path.FlowIds);
        Assert.True(BikeLeasing.GatewayIsSolvent.Instance.Next.SubProcessConcludeContract.Flow.IsDefault);
    }

    [Fact]
    public void Compensation_records_each_handler_after_the_event_that_throws_it()
    {
        var path = ProcessPath.From(BikeLeasing.StartEventApplicationWithdrawn.Instance)
            .Then(next => next.EventReverseApplication)
            .ThrowingCompensation(BikeLeasing.BoundaryCompensateContract.Instance, next => next.ServiceTaskCancelContract, includeBoundaryEvent: true)
            .ThrowingCompensation(BikeLeasing.BoundaryCompensateOrder.Instance, next => next.CallActivityCancelBikeOrder)
            .Then(next => next.ServiceTaskSendCancellationConfirmation)
            .Then(next => next.EndEventApplicationCancelled);

        Assert.Equal(
            new[]
            {
                "startEvent_applicationWithdrawn",
                "event_reverseApplication",
                "boundary_compensateContract",
                "serviceTask_cancelContract",
                "callActivity_cancelBikeOrder",
                "serviceTask_sendCancellationConfirmation",
                "endEvent_applicationCancelled",
            },
            path.Ids);
    }

    [Fact]
    public void JumpTo_re_anchors_to_the_fork_to_walk_the_second_branch_in_one_chain()
    {
        var path = ProcessPath.From(BikeLeasing.GatewayFork.Instance)
            .Then(next => next.ServiceTaskOrderBike)
            .JumpTo(BikeLeasing.GatewayFork.Instance)
            .Then(next => next.ServiceTaskIssueInsurancePolicy);

        Assert.Equal(new[] { "gateway_fork", "serviceTask_orderBike", "serviceTask_issueInsurancePolicy" }, path.Ids);
    }

    [Fact]
    public void Nodes_expose_their_metadata_and_facets_through_the_runtime_types()
    {
        Assert.Equal(BpmnElementType.StartEvent, BikeLeasing.StartEventLeasingRequestReceived.Instance.ElementType);
        Assert.Equal(BpmnEventType.Message, BikeLeasing.StartEventLeasingRequestReceived.Instance.EventType);
        Assert.Equal(new MessageName("miravelo.leasingRequestReceived"), BikeLeasing.StartEventLeasingRequestReceived.Instance.Message);
        Assert.Equal(new BpmnErrorDefinition("miravelo.applicationInvalid", "applicationInvalid"), BikeLeasing.BoundaryApplicationInvalid.Instance.Error);
        Assert.Equal(new BpmnTimer(TimerType.Duration, "P7D"), BikeLeasing.TimerSignatureReminder.Instance.Timer);
        Assert.Same(BikeLeasing.SubProcessConcludeContract.Instance, BikeLeasing.TimerSignatureReminder.Instance.AttachedTo);
        Assert.False(BikeLeasing.TimerSignatureReminder.Instance.IsInterrupting);
        Assert.IsAssignableFrom<IBoundaryEvent<IFlowNode>>(BikeLeasing.TimerSignatureReminder.Instance);
        Assert.Equal(new ProcessId("cancelBikeOrder"), BikeLeasing.CallActivityCancelBikeOrder.Instance.CalledProcess);
        Assert.Equal("orderIds", BikeLeasing.CallActivityCancelBikeOrder.Instance.Inputs.OrderIds.Target);
        Assert.Equal(ProcessVariables.ApplicationId, BikeLeasing.ServiceTaskSendContract.Instance.Variables.ApplicationId.Value);
        Assert.Equal(BikeLeasingProcessApi.FlowNodes.All.Count, BikeLeasingProcessApi.FlowNodes.All.Select(node => node.Id).Distinct().Count());
    }

    [Fact]
    public void Shared_definitions_carry_the_runtime_types_and_list_them_all()
    {
        Assert.Contains(Messages.MiraveloLeasingRequestReceived, Messages.All);
        Assert.Equal("applicationInvalid", Errors.MiraveloApplicationInvalid.Code);
        Assert.Contains(ServiceTasks.CancelContractDelegate, ServiceTasks.All);
    }
}
