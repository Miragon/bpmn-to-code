# Camunda 7 family, embedded · JavaDelegate (Java)

Use this when the engine runs inside the Spring Boot app (`…-bpm-spring-boot-starter…`) and a service task calls a Spring bean through `camunda:delegateExpression`. It covers Camunda 7, CIB seven, Operaton and Fluxnova. Only the import root differs:

| Engine | Import root |
|---|---|
| Camunda 7 | `org.camunda.bpm.engine` |
| CIB seven | `org.cibseven.bpm.engine` |
| Operaton | `org.operaton.bpm.engine` |
| Fluxnova | `org.finos.fluxnova.bpm.engine` |

## Delegate

```java
package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.Errors;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.cibseven.bpm.engine.delegate.BpmnError;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class OrderBikeDelegate implements JavaDelegate {

    private final OrderBikeUseCase useCase;

    public OrderBikeDelegate(OrderBikeUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void execute(DelegateExecution execution) {
        ApplicationId applicationId = ApplicationId.of(execution.getProcessBusinessKey());
        String bikeId = (String) execution.getVariable(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.getValue());
        OrderId orderId;
        try {
            orderId = useCase.orderBike(applicationId, new BikeId(bikeId));
        } catch (BikeUnavailableException e) {
            throw new BpmnError(Errors.BIKE_UNAVAILABLE.getCode(), e.getMessage());
        }
        execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), orderId.value());
    }
}
```

- If the project has a `BaseDelegate` (logs the failure, then rethrows), extend it and override `executeTask` instead of `execute`.
- The **business key is the domain id**. The process adapter sets it when it starts the instance. Read every other input through `FlowNodes.<Node>.Variables`.
- Write plain values (`String`, `Boolean`, numbers), never domain objects. The engine serializes objects into its database, so a later class change breaks running instances.
- Package `adapter.inbound.<engine>` (`cibseven`, `camunda`, `operaton`, `fluxnova`). Name the class after the element and add the suffix `Delegate`.

## Wiring: bean name and delegate expression

The model holds `camunda:delegateExpression="#{orderBikeDelegate}"`. bpmn-to-code takes that expression as the job type: `ServiceTasks.ORDER_BIKE_DELEGATE = "#{orderBikeDelegate}"`. The engine resolves the name inside `#{…}` or `${…}` as a Spring bean, and `@Component` names a bean after its class with a lowercase first letter:

| Class | Bean | Generated constant |
|---|---|---|
| `OrderBikeDelegate` | `orderBikeDelegate` | `ServiceTasks.ORDER_BIKE_DELEGATE` |

If the class name has to differ, name the bean explicitly with `@Component("orderBikeDelegate")`. The compiler does not check a mismatch. It fails at runtime with `Unknown property used in expression`, so let the process test catch it (`/test-process`).

## Errors and transactions

| Outcome | Delegate | Engine |
|---|---|---|
| business error with an error boundary event | `throw new BpmnError(Errors.X.getCode(), message)` | takes the boundary path and commits |
| anything else | let it propagate | rolls back to the last wait state or async point |

How a technical exception ends depends on the async flags in the model:
- **No async point before the task:** the exception reaches whoever moved the token (the REST call that correlated the message, the starting thread), and that caller's transaction rolls back too.
- **`camunda:asyncBefore="true"`:** the task runs as a job. The engine retries it (by default 3 times, immediately), then raises an incident. Set the timing with `<camunda:failedJobRetryTimeCycle>R3/PT10S</camunda:failedJobRetryTimeCycle>`.
- **`camunda:asyncAfter="true"`:** the engine commits right after the task, so a later failure does not run this delegate again. Use it after calls that are not idempotent.

**The embedded engine joins the Spring transaction.** The delegate and the `@Transactional` service it calls run in the same transaction. When the service throws the exception the delegate maps to a `BpmnError`, Spring marks that shared transaction rollback-only. The error path is then never committed and the job fails. Declare the exception on the service:

```java
@Service
@Transactional(noRollbackFor = BikeUnavailableException.class)
public class OrderBikeService implements OrderBikeUseCase { /* … */ }
```

## Pitfalls

- A `BpmnError` that no boundary event catches does not fail. The engine silently ends that execution (unless `enableExceptionsAfterUnhandledBpmnError` is set). Throw only codes the model catches.
- A variable the model does not set makes `getVariable` return `null`. Declare the variable in the model, regenerate, then read it.
- Delegates are singletons shared by all instances. Keep no state in fields.
- Never catch `Exception` broadly to turn it into a `BpmnError`. That hides outages behind the business path.

Reference: https://github.com/miragon-blueprints/cibseven-embedded-example/blob/main/java-maven/service/app/src/main/java/io/miragon/blueprint/adapter/inbound/cibseven/OrderBikeDelegate.java
