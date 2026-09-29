package io.miragon.bpmn.runtime;

import io.miragon.bpmn.runtime.example.Errors;
import io.miragon.bpmn.runtime.example.Messages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared definitions' {@code Names} holders are compile-time constants, so they work as {@code switch} labels.
 */
class GeneratedRawNamesJavaTest {

    @Test
    void messageNamesWorkAsSwitchLabels() {
        assertThat(routeMessage("miravelo.contractSigned")).isEqualTo("contract");
        assertThat(routeMessage("unknown")).isEqualTo("unhandled");
    }

    @Test
    void errorCodesWorkAsSwitchLabels() {
        assertThat(isApplicationInvalid(Errors.MIRAVELO_APPLICATION_INVALID.getCode())).isTrue();
    }

    @Test
    void typedWrappersAreBackedByTheirRawNames() {
        assertThat(Messages.MIRAVELO_CONTRACT_SIGNED.getValue()).isEqualTo(Messages.Names.MIRAVELO_CONTRACT_SIGNED);
        assertThat(Errors.MIRAVELO_APPLICATION_INVALID.getName())
            .isEqualTo(Errors.Names.MIRAVELO_APPLICATION_INVALID_NAME);
    }

    private static String routeMessage(String messageName) {
        return switch (messageName) {
            case Messages.Names.MIRAVELO_CONTRACT_SIGNED -> "contract";
            case Messages.Names.MIRAVELO_ADDRESS_CHANGED -> "address";
            default -> "unhandled";
        };
    }

    private static boolean isApplicationInvalid(String errorCode) {
        return switch (errorCode) {
            case Errors.Names.MIRAVELO_APPLICATION_INVALID_CODE -> true;
            default -> false;
        };
    }
}
