# Zeebe · Job worker (Kotlin)

Applies to Camunda 8 projects on the Camunda Spring SDK (`io.camunda:camunda-spring-boot-starter`): one `@Component` with one `@JobWorker` method per job type, in `adapter.inbound.zeebe`, named `<Element>Worker`.

## Simple variant: auto-complete

Use it when the task has no error boundary event and default retry handling is enough. The SDK completes the job with the returned map, and fails it with `retries - 1` when the method throws.

```kotlin
package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.ProcessVariables
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import org.springframework.stereotype.Component

@Component
class RequestCancellationWorker(
    private val useCase: RequestOrderCancellationUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_REQUEST_CANCELLATION)
    fun handle(@Variable(name = ProcessVariables.ORDER_ID) orderId: String): Map<String, Any> {
        val cancellationPossible = useCase.requestCancellation(OrderId(orderId))
        val output = FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE
        return mapOf(output.value to cancellationPossible)
    }
}
```

Without output variables the method returns `Unit`. To raise a BPMN error from this variant, throw `CamundaError.bpmnError(Errors.X.code, message)` (`io.camunda.client.exception.CamundaError`).

## Manual variant: complete, throw error, fail

Use it when the task has an error boundary event or needs its own retry backoff. With `autoComplete` off, the worker must send exactly one command per job.

```kotlin
@Component
class OrderBikeWorker(
    private val useCase: OrderBikeUseCase,
) {

    private val retryBackoff: Duration = Duration.ofSeconds(10)

    @JobWorker(type = ServiceTasks.MIRAVELO_ORDER_BIKE, autoComplete = [false])
    fun handle(
        client: JobClient,
        job: ActivatedJob,
        @Variable(name = ProcessVariables.APPLICATION_ID) applicationId: String,
        @Variable(name = ProcessVariables.BIKE_ID) bikeId: String,
    ) {
        try {
            val orderId = useCase.orderBike(ApplicationId.of(applicationId), BikeId(bikeId))
            client.newCompleteCommand(job)
                .variables(mapOf(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to orderId.value))
                .send()
                .join()
        } catch (e: BikeUnavailableException) {
            client.newThrowErrorCommand(job)
                .errorCode(Errors.BIKE_UNAVAILABLE.code)
                .errorMessage(e.message)
                .send()
                .join()
        } catch (e: RuntimeException) {
            client.newFailCommand(job)
                .retries(job.retries - 1)
                .retryBackoff(retryBackoff)
                .errorMessage(e.message)
                .send()
                .join()
        }
    }
}
```

Non-obvious imports: `io.camunda.client.annotation.JobWorker`, `io.camunda.client.annotation.Variable`, `io.camunda.client.api.worker.JobClient`, `io.camunda.client.api.response.ActivatedJob`, and `FlowNodes` from `<Process>ProcessApi.FlowNodes`.

## Retries and errors

- The retry budget is `retries` on the task definition in the model (`<zeebe:taskDefinition type="miravelo.orderBike" retries="3" />`). The worker counts it down with `job.retries - 1`; at 0, Zeebe raises an incident on the element.
- Catch the domain exception for the BPMN error before the generic `RuntimeException`.
- A BPMN error that no boundary event catches also raises an incident. Throw only codes the model catches on this element.

## Pitfalls

- `@JobWorker(type = …)` needs a compile-time constant: `ServiceTasks.X` is a `const val`, `FlowNodes.X.jobType` is not and does not compile there.
- `autoComplete` is a `boolean[]` in the annotation, so Kotlin writes `autoComplete = [false]`.
- The SDK fetches only the variables named by `@Variable` parameters. If you read `job.variablesAsMap` instead, list the names in `fetchVariables = [ProcessVariables.X]`.
- `@Variable` is optional by default: a variable missing in scope arrives as `null` and a non-null Kotlin parameter fails the job. The name must be one the model maps into this task, so take it from `ProcessVariables` and check it against `FlowNodes.<Node>.Variables`.
- Output keys are `FlowNodes.<Node>.Variables.X.value`, the `String`; the `VariableName` itself would be serialized as an object.
- Keep `.send().join()`: without `join()` a rejected command goes unnoticed and the job is retried after its timeout.
- Zeebe delivers jobs at least once. The use case must tolerate a second call for the same job.

Reference: https://github.com/miragon-blueprints/zeebe-example/blob/main/kotlin-gradle/service/app/src/main/kotlin/io/miragon/blueprint/adapter/inbound/zeebe/OrderBikeWorker.kt
