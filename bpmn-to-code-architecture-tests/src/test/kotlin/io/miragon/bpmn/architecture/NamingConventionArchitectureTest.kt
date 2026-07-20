package io.miragon.bpmn.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Naming conventions per hexagonal layer, read straight from the Kotlin source with Konsist.
 *
 * Each rule lists the class-name suffixes allowed in a package together with a short rationale via
 * [AllowedSuffix], so the file doubles as living documentation of what each layer's types are called.
 *
 * Outbound-adapter naming is intentionally *not* constrained: `adapter.outbound` legitimately holds a
 * heterogeneous mix (adapters, mappers, loaders, generators, extractors, JSON models, utils, constants,
 * enums), so a suffix whitelist there would be noisy and high-maintenance rather than a useful guardrail.
 */
class NamingConventionArchitectureTest {

    private val rootPackage = "io.miragon.bpmn"

    @Nested
    inner class Ports {

        @Test
        fun `inbound ports are use-cases or queries`() {
            checkNaming(
                packagePattern = "$rootPackage.application.port.inbound..",
                allowedSuffixes = listOf(
                    AllowedSuffix("UseCase", "inbound port for a state-changing operation"),
                    AllowedSuffix("Query", "inbound port for a read-only operation"),
                ),
            )
        }

        @Test
        fun `outbound ports are ports or repositories`() {
            checkNaming(
                packagePattern = "$rootPackage.application.port.outbound..",
                allowedSuffixes = listOf(
                    AllowedSuffix("Port", "generic outbound port delegating to an out-adapter"),
                    AllowedSuffix("Repository", "outbound port for persistence access"),
                ),
            )
        }
    }

    @Nested
    inner class Application {

        @Test
        fun `application services are named with a Service suffix`() {
            checkNaming(
                packagePattern = "$rootPackage.application.service..",
                allowedSuffixes = listOf(
                    AllowedSuffix("Service", "orchestrates a single use case"),
                ),
            )
        }
    }

    @Nested
    inner class InboundAdapters {

        @Test
        fun `inbound adapters are plugins`() {
            checkNaming(
                packagePattern = "$rootPackage.adapter.inbound..",
                allowedSuffixes = listOf(
                    AllowedSuffix("Plugin", "driving entry point wiring a use case to its adapters"),
                ),
            )
        }
    }

    private fun checkNaming(
        packagePattern: String,
        allowedSuffixes: List<AllowedSuffix>,
    ) {
        Konsist
            .scopeFromPackage(packagePattern)
            .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
            .filter { it.path.contains("/src/main/") }
            .assertTrue { declaration ->
                allowedSuffixes.any { declaration.hasNameEndingWith(it.suffix) }
            }
    }

    /**
     * An allowed class-name suffix together with a short rationale. The [reason] documents *why* the
     * suffix is allowed and doubles as inline documentation; it is not part of Konsist's failure message.
     */
    private data class AllowedSuffix(
        val suffix: String,
        val reason: String,
    )
}
