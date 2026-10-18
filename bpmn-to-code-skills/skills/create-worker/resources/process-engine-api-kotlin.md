# process-engine-api · Worker (Kotlin)

Applies to projects on `dev.bpm-crafters.process-engine-worker` (`process-engine-worker-spring-boot-starter`) over any process-engine-api adapter: Camunda 7, CIB seven, Operaton (embedded or remote) or Camunda 8. The worker code is the same for all of them. One `@Component` with one `@ProcessEngineWorker` method per topic, in `adapter.inbound.<engine>` (blueprint: `adapter.inbound.cibseven`), named `<Element>Worker`.

## Worker

```kotlin
package io.miragon.blueprint.adapter.inbound.cibseven

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Errors
import io.miragon.blueprint.adapter.process.ProcessVariables
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class OrderBikeWorker(
    private val useCase: OrderBikeUseCase,
) {

    @ProcessEngineWorker(topic = ServiceTasks.ORDER_BIKE)
    fun orderBike(
        @Variable(name = ProcessVariables.APPLICATION_ID) applicationId: String,
        @Variable(name = ProcessVariables.BIKE_ID) bikeId: String,
    ): Map<String, Any?> {
        val orderId = try {
            useCase.orderBike(ApplicationId.of(applicationId), BikeId(bikeId))
        } catch (e: BikeUnavailableException) {
            throw BpmnErrorOccurred(e.message.orEmpty(), Errors.BIKE_UNAVAILABLE.code, emptyMap())
        }
        val output = FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID
        return mapOf(output.value to orderId.value)
    }
}
```

- **Inputs:** `@Variable` parameters. They are mandatory by default: a missing variable fails the task before the method runs. For an optional input write `@Variable(name = ProcessVariables.X, mandatory = false) x: String?`.
- **Outputs:** the returned `Map` is the completion payload. Without outputs, return `Unit` and the task completes empty.
- **Task metadata:** add a `TaskInformation` parameter (task id, `meta`, retries) if the use case needs it.
- Only the `@Variable` names are fetched from the engine. Declare every input the method reads.

## Errors and retries

| Thrown from the method | What the library does |
|---|---|
| `BpmnErrorOccurred(message, Errors.X.code, payload)` | completes the task by error; the boundary event with that code catches it |
| `FailJobException(message, cause, retryCount, retryBackoff)` | fails the task with exactly these retries and backoff |
| any other exception | fails the task with `retries - 1`; at 0 the engine raises an incident |

`BpmnErrorOccurred` is a checked exception (`open`, so a domain exception may extend it). Throw only codes the model catches on this element, otherwise the engine raises an incident.

## Transactions

```kotlin
@Transactional
@ProcessEngineWorker(topic = ServiceTasks.ORDER_BIKE, completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
```

- Without `@Transactional` (the blueprint) the use case owns its transaction and the library completes the task afterwards.
- With `@Transactional` on the method or class, the library runs the method in a transaction, and `completion` decides when the task completes:
  - `BEFORE_COMMIT`: complete inside the transaction. On an embedded C7-family engine sharing the datasource, business data and completion commit together.
  - `AFTER_COMMIT`: commit first, then complete. A failed completion redelivers the task and the worker runs again.
  - `DEFAULT` follows `dev.bpm-crafters.process-api.worker.complete-tasks-before-commit` (default `false`).
- Spring commits on checked exceptions, so state written before a `BpmnErrorOccurred` stays. If a `@Transactional` use case throws the domain exception, it rolls back unless the use case declares `noRollbackFor`.

## Idempotency

Delivery is at least once. The use case must tolerate a second call for the same task, or register an `IdempotencyRegistry` bean: the library then returns the stored result instead of calling the method again. `InMemoryIdempotencyRegistry` suits tests and single nodes. For clusters use `JpaIdempotencyRegistry` from `process-engine-worker-spring-boot-idempotency-registry-jpa`. The default is `NoOpIdempotencyRegistry`.

## Pitfalls

- Annotation arguments must be compile-time constants: `ServiceTasks.X` and `ProcessVariables.X` are `const val`. `FlowNodes.X.Variables.Y` is not and does not compile there. Check that each name appears in `FlowNodes.<Node>.Variables`.
- A bare `@Variable` falls back to the parameter name, so renaming the parameter breaks the task at runtime. Always pass `name`.
- Output keys are `FlowNodes.<Node>.Variables.X.value`, the `String`. The `VariableName` object would be serialized as an object.
- The topic is the external task topic, so the model needs `camunda:type="external"` with `camunda:topic` (C7 family) or a `zeebe:taskDefinition type` (C8). A delegate expression is never picked up.
- The engine adapter's service-task delivery must be enabled, e.g. `dev.bpm-crafters.process-api.adapter.cib-seven-embedded.service-tasks.delivery-strategy`. Otherwise no worker is ever called.

Reference: https://github.com/miragon-blueprints/cibseven-process-engine-api-example/blob/main/kotlin-gradle/service/app/src/main/kotlin/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeWorker.kt
