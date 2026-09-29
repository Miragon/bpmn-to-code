package io.miragon.bpmn.runtime;

import io.miragon.bpmn.runtime.example.BikeLeasingProcessApi.FlowNodes;
import io.miragon.bpmn.runtime.example.Errors;
import io.miragon.bpmn.runtime.example.Escalations;
import io.miragon.bpmn.runtime.example.Messages;
import io.miragon.bpmn.runtime.example.ServiceTasks;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enumerates the generated Java bike-leasing API via its {@code all()} accessors. The
 * {@code GeneratedApiEnumerationKotlinTest} sibling covers Kotlin's {@code entries} and sealed {@code FlowNodes.Node}.
 */
class GeneratedApiEnumerationJavaTest {

    @Test
    void flowAllListsExactlyTheNodesOfTheProcess() {
        var everyNodeClass = Arrays.stream(FlowNodes.class.getDeclaredClasses()).filter(FlowNode.class::isAssignableFrom).toList();

        assertThat(FlowNodes.all()).hasSize(42).extracting(Object::getClass).containsExactlyInAnyOrderElementsOf(everyNodeClass);
    }

    @Test
    void sharedDefinitionsListExactlyTheirValuesInDeclarationOrder() {
        assertThat(ServiceTasks.all()).containsExactly(
            ServiceTasks.CANCEL_CONTRACT_DELEGATE,
            ServiceTasks.MAIL_SERVICE_SEND_REMINDER_APPLICATION_ID_,
            ServiceTasks.SEND_CONTRACT_DELEGATE,
            ServiceTasks.VALIDATE_APPLICATION_DELEGATE,
            ServiceTasks.IO_MIRAVELO_LEASING_ISSUE_INSURANCE_POLICY_DELEGATE,
            ServiceTasks.MIRAVELO_CANCEL_POLICY,
            ServiceTasks.MIRAVELO_ORDER_BIKE,
            ServiceTasks.MIRAVELO_SEND_CANCELLATION_CONFIRMATION,
            ServiceTasks.MIRAVELO_SEND_REJECTION
        );
        assertThat(Messages.all()).containsExactly(
            Messages.MIRAVELO_ADDRESS_CHANGED,
            Messages.MIRAVELO_APPLICATION_WITHDRAWN,
            Messages.MIRAVELO_CONTRACT_SIGNED,
            Messages.MIRAVELO_HANDOVER_REPORTED,
            Messages.MIRAVELO_LEASING_REQUEST_RECEIVED
        );
        assertThat(Errors.all()).containsExactly(Errors.MIRAVELO_APPLICATION_INVALID);
        assertThat(Escalations.all()).containsExactly(Escalations.MIRAVELO_CONTRACT_NOT_SIGNED);
    }
}
