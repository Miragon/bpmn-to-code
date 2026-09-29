package io.miragon.bpmn.adapter;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Reads an enum-valued mojo parameter, failing the build with the valid values instead of a raw exception.
 */
final class EnumParameter {

	private EnumParameter() {
	}

	static <E extends Enum<E>> E parse(String name, String value, Class<E> type) throws MojoFailureException {
		String validValues = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
		if (value == null || value.isBlank()) {
			throw new MojoFailureException(name + " is required (valid values: " + validValues + ")");
		}
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException unknownValue) {
			throw new MojoFailureException(name + " '" + value + "' is not supported (valid values: " + validValues + ")");
		}
	}
}
