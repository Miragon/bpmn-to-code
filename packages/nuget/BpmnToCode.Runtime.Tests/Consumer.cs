using System;
using MiraVelo.Process;
using Miragon.BpmnToCode.Runtime;
using Api = MiraVelo.Process.BikeLeasingProcessApi;

namespace MiraVelo.Process.Consumer;

[AttributeUsage(AttributeTargets.Method, AllowMultiple = true)]
public sealed class JobTypeAttribute : Attribute
{
    public JobTypeAttribute(string type) => Type = type;
    public string Type { get; }
}

public static class Consumer
{
    [JobType(ServiceTasks.MiraveloSendContract)]
    public static string Describe(string jobType)
    {
        switch (jobType)
        {
            case ServiceTasks.MiraveloSendContract:
                return "contract";
            default:
                return "other";
        }
    }

    public static void Navigate()
    {
        var start = Api.FlowNodes.StartEventLeasingRequestReceived;
        var successor = start.Next.ServiceTaskValidateApplication;
        var edge = successor.Flow;
        string? condition = edge.ConditionExpression;
        bool isDefault = edge.IsDefault;
        Api.FlowNodes.ServiceTaskValidateApplicationNode target = edge.Target;
        ISequenceFlow generic = edge;
        IHasJobType jobTask = Api.FlowNodes.ServiceTaskSendContract;
        string jobType = jobTask.JobType;
        if (!ReferenceEquals(generic.Target, target)) throw new InvalidOperationException();
        if (!edge.Equals(start.Next.ServiceTaskValidateApplication.Flow)) throw new InvalidOperationException();
        ISuccessor<IFlowNode> genericSuccessor = successor;
        IBoundaryEvent boundary = Api.FlowNodes.TimerSignatureReminder;
        AttachedBoundaryEvent<Api.FlowNodes.TimerSignatureReminderNode> attached = Api.FlowNodes.SubProcessConcludeContract.Next.TimerSignatureReminder;
        ICompensationThrowEvent thrower = Api.FlowNodes.EventReverseApplication;
        AssociatedCompensationHandler<Api.FlowNodes.ServiceTaskCancelContractNode> handler = Api.FlowNodes.BoundaryCompensateContract.Next.ServiceTaskCancelContract;

        var creditRating = successor.Target.Next.BusinessRuleTaskCheckCreditRating.Target;
        var subProcess = creditRating.Next.GatewayIsSolvent.Target.Next.SubProcessConcludeContract.Target;
        var innerStart = subProcess.Start.StartEventCustomerEligible;
        string? innerName = innerStart.Name;
        string hostId = Api.FlowNodes.TimerSignatureReminder.AttachedTo.Id.Value;
        bool interrupts = Api.FlowNodes.TimerSignatureReminder.IsInterrupting;
        VariableName.Input input = Api.FlowNodes.ServiceTaskSendContract.Variables.ApplicationId;
        IHasVariables withVariables = Api.FlowNodes.ServiceTaskSendContract;
        int declared = withVariables.Variables.All.Count + withVariables.Variables.Inputs.Count + withVariables.Variables.Outputs.Count;
        if (declared == 0) throw new InvalidOperationException();
        ProcessId called = Api.FlowNodes.CallActivityCancelBikeOrder.CalledProcess;
        InputOutputMapping mapping = Api.FlowNodes.CallActivityCancelBikeOrder.Inputs.OrderIds;

        ElementId other = NestedSubprocessProcessProcessApi.FlowNodes.StartEventRoot.Id;
        IFlowNode[] acrossProcesses = { Api.FlowNodes.ServiceTaskSendContract, NestedSubprocessProcessProcessApi.FlowNodes.StartEventRoot };
        string rawId = Api.FlowNodes.ServiceTaskSendContract.Id;
        string rawProcess = Api.FlowNodes.CallActivityCancelBikeOrder.CalledProcess;
        if (acrossProcesses.Length != 2 || rawId.Length == 0 || rawProcess.Length == 0) throw new InvalidOperationException();
        if (!ReferenceEquals(start.Next, start.Next)) throw new InvalidOperationException("Next allocates on every read");
        if (!ReferenceEquals(start.Next.ServiceTaskValidateApplication, start.Next.ServiceTaskValidateApplication)) throw new InvalidOperationException("a successor allocates on every read");
        if (!ReferenceEquals(Api.FlowNodes.SubProcessConcludeContract.Start, Api.FlowNodes.SubProcessConcludeContract.Start)) throw new InvalidOperationException("Start allocates on every read");
        Console.WriteLine($"{condition} {isDefault} {innerName} {hostId} {interrupts} {input} {called} {mapping} {other} {boundary.Id} {genericSuccessor.Target.Id} {attached.Target.Id} {handler.Target.Id} {thrower.Id}");
    }
}
