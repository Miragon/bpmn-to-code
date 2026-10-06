package io.miragon.bpmn.adapter.outbound.engine

import io.miragon.bpmn.adapter.outbound.engine.dialect.ZeebeDialect
import io.miragon.bpmn.domain.ProcessModel
import io.miragon.bpmn.domain.shared.FlowNodeDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CompensationAssociationExtractionTest {

    private val underTest = ProcessModelReader(ZeebeDialect())

    private val model = underTest.read(COMPENSATION_ASSOCIATIONS.toByteArray())

    @Test
    fun `an association inside a sub-process links its compensation boundary event to the handler`() {
        assertThat(model.compensationHandlerOf("boundary_nested")).isEqualTo("serviceTask_cancelContract")
    }

    @Test
    fun `an association to a text annotation is no compensation handler`() {
        assertThat(model.compensationHandlerOf("boundary_annotated")).isNull()
    }

    @Test
    fun `an association to an activity that is not for compensation is no compensation handler`() {
        assertThat(model.compensationHandlerOf("boundary_plainTarget")).isNull()
    }

    @Test
    fun `an association pointing from the handler to the boundary event is ignored`() {
        assertThat(model.compensationHandlerOf("boundary_reversed")).isNull()
    }

    @Test
    fun `an association to a missing element is ignored`() {
        assertThat(model.compensationHandlerOf("boundary_dangling")).isNull()
    }

    @Test
    fun `an association from a boundary event that does not compensate is ignored`() {
        assertThat(model.compensationHandlerOf("boundary_timer")).isNull()
    }

    @Test
    fun `the first association wins when a compensation boundary event has several handlers`() {
        assertThat(model.compensationHandlerOf("boundary_twoHandlers")).isEqualTo("serviceTask_cancelBikeOrder")
    }

    @Test
    fun `an association to a handler of another process is ignored`() {
        assertThat(model.compensationHandlerOf("boundary_foreignHandler")).isNull()
    }

    private fun ProcessModel.compensationHandlerOf(eventId: String): String? {
        val event = allFlowNodes.single { it.id == eventId } as FlowNodeDefinition.Event
        return event.compensationHandlerRef
    }

    private companion object {
        val COMPENSATION_ASSOCIATIONS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                id="Definitions_CompensationAssociations" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="bikeLeasingCompensation" isExecutable="true">
                <bpmn:subProcess id="subProcess_concludeContract">
                  <bpmn:serviceTask id="serviceTask_sendContract" />
                  <bpmn:boundaryEvent id="boundary_nested" attachedToRef="serviceTask_sendContract">
                    <bpmn:compensateEventDefinition id="compensateEventDefinition_nested" />
                  </bpmn:boundaryEvent>
                  <bpmn:serviceTask id="serviceTask_cancelContract" isForCompensation="true" />
                  <bpmn:association id="association_nested" associationDirection="One" sourceRef="boundary_nested" targetRef="serviceTask_cancelContract" />
                </bpmn:subProcess>
                <bpmn:serviceTask id="serviceTask_orderBike" />
                <bpmn:boundaryEvent id="boundary_annotated" attachedToRef="serviceTask_orderBike">
                  <bpmn:compensateEventDefinition id="compensateEventDefinition_annotated" />
                </bpmn:boundaryEvent>
                <bpmn:boundaryEvent id="boundary_plainTarget" attachedToRef="serviceTask_orderBike">
                  <bpmn:compensateEventDefinition id="compensateEventDefinition_plainTarget" />
                </bpmn:boundaryEvent>
                <bpmn:boundaryEvent id="boundary_reversed" attachedToRef="serviceTask_orderBike">
                  <bpmn:compensateEventDefinition id="compensateEventDefinition_reversed" />
                </bpmn:boundaryEvent>
                <bpmn:boundaryEvent id="boundary_dangling" attachedToRef="serviceTask_orderBike">
                  <bpmn:compensateEventDefinition id="compensateEventDefinition_dangling" />
                </bpmn:boundaryEvent>
                <bpmn:boundaryEvent id="boundary_timer" attachedToRef="serviceTask_orderBike">
                  <bpmn:timerEventDefinition id="timerEventDefinition_timer">
                    <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT1H</bpmn:timeDuration>
                  </bpmn:timerEventDefinition>
                </bpmn:boundaryEvent>
                <bpmn:boundaryEvent id="boundary_twoHandlers" attachedToRef="serviceTask_orderBike">
                  <bpmn:compensateEventDefinition id="compensateEventDefinition_twoHandlers" />
                </bpmn:boundaryEvent>
                <bpmn:boundaryEvent id="boundary_foreignHandler" attachedToRef="serviceTask_orderBike">
                  <bpmn:compensateEventDefinition id="compensateEventDefinition_foreignHandler" />
                </bpmn:boundaryEvent>
                <bpmn:serviceTask id="serviceTask_notifyCustomer" />
                <bpmn:serviceTask id="serviceTask_cancelBikeOrder" isForCompensation="true" />
                <bpmn:serviceTask id="serviceTask_refundDeposit" isForCompensation="true" />
                <bpmn:textAnnotation id="textAnnotation_note">
                  <bpmn:text>Compensated by the bike dealer</bpmn:text>
                </bpmn:textAnnotation>
                <bpmn:association id="association_annotated" sourceRef="boundary_annotated" targetRef="textAnnotation_note" />
                <bpmn:association id="association_plainTarget" associationDirection="One" sourceRef="boundary_plainTarget" targetRef="serviceTask_notifyCustomer" />
                <bpmn:association id="association_reversed" associationDirection="One" sourceRef="serviceTask_cancelBikeOrder" targetRef="boundary_reversed" />
                <bpmn:association id="association_dangling" associationDirection="One" sourceRef="boundary_dangling" targetRef="serviceTask_missing" />
                <bpmn:association id="association_timer" associationDirection="One" sourceRef="boundary_timer" targetRef="serviceTask_cancelBikeOrder" />
                <bpmn:association id="association_firstHandler" associationDirection="One" sourceRef="boundary_twoHandlers" targetRef="serviceTask_cancelBikeOrder" />
                <bpmn:association id="association_secondHandler" associationDirection="One" sourceRef="boundary_twoHandlers" targetRef="serviceTask_refundDeposit" />
                <bpmn:association id="association_foreignHandler" associationDirection="One" sourceRef="boundary_foreignHandler" targetRef="serviceTask_cancelPolicy" />
              </bpmn:process>
              <bpmn:process id="insurancePolicy" isExecutable="false">
                <bpmn:serviceTask id="serviceTask_cancelPolicy" isForCompensation="true" />
              </bpmn:process>
            </bpmn:definitions>
        """.trimIndent()
    }
}
