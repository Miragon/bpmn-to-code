# Zeebe · Job worker (Java)

Applies to Camunda 8 projects on the Camunda Spring SDK (`io.camunda:camunda-spring-boot-starter`): one `@Component` with one `@JobWorker` method per job type, in `adapter.inbound.zeebe`, named `<Element>Worker`.

## Simple variant: auto-complete

Use it when the task has no error boundary event and default retry handling is enough. The SDK completes the job with the returned map, and fails it with `retries - 1` when the method throws.

```java
package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.ProcessVariables;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RequestCancellationWorker {

    private final RequestOrderCancellationUseCase useCase;

    public RequestCancellationWorker(RequestOrderCancellationUseCase useCase) {
        this.useCase = useCase;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_REQUEST_CANCELLATION)
    public Map<String, Object> handle(@Variable(name = ProcessVariables.ORDER_ID) String orderId) {
        boolean cancellationPossible = useCase.requestCancellation(new OrderId(orderId));
        String output = FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.getValue();
        return Map.of(output, cancellationPossible);
    }
}
```

Without output variables the method returns `void`. To raise a BPMN error from this variant, throw `CamundaError.bpmnError(Errors.X.getCode(), message)` (`io.camunda.client.exception.CamundaError`).

## Manual variant: complete, throw error, fail

Use it when the task has an error boundary event or needs its own retry backoff. With `autoComplete` off, the worker must send exactly one command per job.

```java
@JobWorker(type = ServiceTasks.MIRAVELO_ORDER_BIKE, autoComplete = false)
public void handle(
    JobClient client,
    ActivatedJob job,
    @Variable(name = ProcessVariables.APPLICATION_ID) String applicationId,
    @Variable(name = ProcessVariables.BIKE_ID) String bikeId
) {
    try {
        OrderId orderId = useCase.orderBike(ApplicationId.of(applicationId), new BikeId(bikeId));
        client.newCompleteCommand(job)
            .variables(Map.of(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), orderId.value()))
            .send()
            .join();
    } catch (BikeUnavailableException e) {
        client.newThrowErrorCommand(job)
            .errorCode(Errors.BIKE_UNAVAILABLE.getCode())
            .errorMessage(e.getMessage())
            .send()
            .join();
    } catch (RuntimeException e) {
        client.newFailCommand(job)
            .retries(job.getRetries() - 1)
            .retryBackoff(retryBackoff)
            .errorMessage(e.getMessage())
            .send()
            .join();
    }
}
```

`retryBackoff` is a field: `private final Duration retryBackoff = Duration.ofSeconds(10);`. Non-obvious imports: `io.camunda.client.annotation.JobWorker`, `io.camunda.client.annotation.Variable`, `io.camunda.client.api.worker.JobClient`, `io.camunda.client.api.response.ActivatedJob`, and `FlowNodes` from `<Process>ProcessApi.FlowNodes`.

## Retries and errors

- The retry budget is `retries` on the task definition in the model (`<zeebe:taskDefinition type="miravelo.orderBike" retries="3" />`). The worker counts it down with `job.getRetries() - 1`; at 0, Zeebe raises an incident on the element.
- Catch the domain exception for the BPMN error before the generic `RuntimeException`.
- A BPMN error that no boundary event catches also raises an incident. Throw only codes the model catches on this element.

## Pitfalls

- `@JobWorker(type = …)` needs a compile-time constant: `ServiceTasks.X` is a `static final String`, `FlowNodes.serviceTaskOrderBike().getJobType()` is not.
- The SDK fetches only the variables named by `@Variable` parameters. If you read `job.getVariablesAsMap()` instead, list the names in `fetchVariables = {ProcessVariables.X}`.
- Always set `@Variable(name = …)`: a bare `@Variable` relies on parameter names, which need the `-parameters` compiler flag. The name must be one the model maps into this task; check it against `FlowNodes.<Node>.Variables`.
- `@Variable` is optional by default: a variable missing in scope arrives as `null`.
- Output keys are `FlowNodes.<Node>.Variables.X.getValue()`, the `String`; the `VariableName` itself would be serialized as an object.
- Keep `.send().join()`: without `join()` a rejected command goes unnoticed and the job is retried after its timeout.
- Zeebe delivers jobs at least once. The use case must tolerate a second call for the same job.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/java-maven/service/app/src/main/java/io/miragon/blueprint/adapter/inbound/zeebe/OrderBikeWorker.java
