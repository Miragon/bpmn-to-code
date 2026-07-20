package io.miragon.bpmn.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Enforces the hexagonal (ports & adapters) structure of `bpmn-to-code-core`: layer dependencies,
 * technology-neutrality of the domain, port/adapter typing and package placement.
 *
 * Naming conventions live in [NamingConventionArchitectureTest], general Kotlin coding guidelines in
 * [CodingGuidelinesTest], and cross-module import boundaries in [ExternalModuleImportTest]. Everything
 * runs on Konsist, which reads the project's Kotlin source straight off disk — so this module depends
 * on no other module (see the build file).
 */
class HexagonalArchitectureTest {

    private val rootPackage = "io.miragon.bpmn"

    @Nested
    inner class Dependencies {

        @Test
        fun `hexagonal architecture layers are respected`() {
            Konsist
                .scopeFromProject()
                .assertArchitecture {
                    val domainLayer = Layer("Domain", "$rootPackage.domain..")
                    val inPortsLayer = Layer("In-Ports", "$rootPackage.application.port.inbound..")
                    val outPortsLayer = Layer("Out-Ports", "$rootPackage.application.port.outbound..")
                    val inAdaptersLayer = Layer("In-Adapters", "$rootPackage.adapter.inbound..")
                    val outAdaptersLayer = Layer("Out-Adapters", "$rootPackage.adapter.outbound..")
                    val applicationLayer = Layer("Application", "$rootPackage.application.service..")

                    domainLayer.dependsOnNothing()
                    inPortsLayer.dependsOn(domainLayer)
                    outPortsLayer.dependsOn(domainLayer)
                    outAdaptersLayer.dependsOn(domainLayer, outPortsLayer)
                    applicationLayer.dependsOn(domainLayer, inPortsLayer, outPortsLayer, outAdaptersLayer)
                    inAdaptersLayer.dependsOn(domainLayer, inPortsLayer, applicationLayer)
                }
        }

        @Test
        fun `domain only depends on language, logging and its own code`() {
            Konsist
                .scopeFromPackage("$rootPackage.domain..")
                .files
                .filter { it.path.contains("/src/main/") }
                .assertTrue(testName = "domain files should only import language, logging or domain code") { file ->
                    file.imports.all { import ->
                        DOMAIN_ALLOWED_IMPORT_PREFIXES.any { import.name.startsWith(it) }
                    }
                }
        }

        @Test
        fun `services do not depend on other application services`() {
            Konsist
                .scopeFromPackage("$rootPackage.application.service..")
                .files
                .filter { it.path.contains("/src/main/") }
                .assertTrue { file ->
                    file.imports.none { it.name.startsWith("$rootPackage.application.service.") }
                }
        }
    }

    @Nested
    inner class Ports {

        @Test
        fun `all ports are interfaces`() {
            Konsist
                .scopeFromPackage("$rootPackage.application.port..")
                .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
                .assertTrue { it is KoInterfaceDeclaration }
        }
    }

    @Nested
    inner class Services {

        @Test
        fun `each service imports exactly one inbound port`() {
            Konsist
                .scopeFromPackage("$rootPackage.application.service..")
                .files
                .filter { it.path.contains("/src/main/") }
                .assertTrue { file ->
                    val inboundPortImports = file.imports
                        .filter { it.name.contains(".application.port.inbound.") }
                    inboundPortImports.size == 1
                }
        }

        @Test
        fun `service constructor parameters are typed as out-port interfaces, not concrete adapter implementations`() {
            // Services may import concrete out-adapter classes as constructor default values (no DI framework),
            // but the constructor parameter TYPES must use out-port interfaces, not the concrete adapter classes.
            Konsist
                .scopeFromPackage("$rootPackage.application.service..")
                .files
                .filter { it.path.contains("/src/main/") }
                .assertTrue { file ->
                    val outAdapterImportNames = file.imports
                        .filter { it.name.startsWith("$rootPackage.adapter.outbound.") }
                        .map { it.name.substringAfterLast(".") }
                    val constructorParamTypeNames = file.classes()
                        .flatMap { it.primaryConstructor?.parameters ?: emptyList() }
                        .map { it.type.name }
                    outAdapterImportNames.none { it in constructorParamTypeNames }
                }
        }
    }

    @Nested
    inner class Structure {

        // Scoped to core: the inbound/outbound hexagon layout is a `bpmn-to-code-core` convention. The
        // plugin modules keep their own adapter classes directly under `adapter` (e.g. Gradle tasks).

        @Test
        fun `application classes reside in service or port sub-packages`() {
            Konsist
                .scopeFromPackage("$rootPackage.application..")
                .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
                .filter { it.path.contains(CORE_MAIN_PATH) }
                .assertTrue { declaration ->
                    declaration.resideInPackage("$rootPackage.application.service..") ||
                        declaration.resideInPackage("$rootPackage.application.port..")
                }
        }

        @Test
        fun `adapter classes reside in inbound or outbound sub-packages`() {
            Konsist
                .scopeFromPackage("$rootPackage.adapter..")
                .classesAndInterfacesAndObjects(includeNested = false, includeLocal = false)
                .filter { it.path.contains(CORE_MAIN_PATH) }
                .assertTrue { declaration ->
                    declaration.resideInPackage("$rootPackage.adapter.inbound..") ||
                        declaration.resideInPackage("$rootPackage.adapter.outbound..")
                }
        }
    }

    @Nested
    inner class InAdapters {

        @Test
        fun `in-adapter constructor parameters are typed as inbound port interfaces, not concrete service implementations`() {
            // In-adapters may import concrete service classes as constructor default values (no DI framework),
            // but the constructor parameter TYPES must use inbound port interfaces, not the concrete service classes.
            Konsist
                .scopeFromPackage("$rootPackage.adapter.inbound..")
                .files
                .filter { it.path.contains("/src/main/") }
                .assertTrue { file ->
                    val serviceImportNames = file.imports
                        .filter { it.name.startsWith("$rootPackage.application.service.") }
                        .map { it.name.substringAfterLast(".") }
                    val constructorParamTypeNames = file.classes()
                        .flatMap { it.primaryConstructor?.parameters ?: emptyList() }
                        .map { it.type.name }
                    serviceImportNames.none { it in constructorParamTypeNames }
                }
        }

        @Test
        fun `each in-adapter fulfils at most one use-case`() {
            Konsist
                .scopeFromPackage("$rootPackage.adapter.inbound..")
                .files
                .filter { it.path.contains("/src/main/") }
                .assertTrue { file ->
                    val inboundPortImports = file.imports
                        .filter { it.name.contains(".application.port.inbound.") }
                    inboundPortImports.size <= 1
                }
        }
    }

    private companion object {

        /** Restricts a whole-project scan to `bpmn-to-code-core`'s production sources. */
        const val CORE_MAIN_PATH = "/bpmn-to-code-core/src/main/"

        /**
         * Package prefixes the technology-neutral domain may import from: the Kotlin & Java languages,
         * the kotlin-logging facade ([io.github.oshai]) used by domain services, and the domain itself.
         */
        val DOMAIN_ALLOWED_IMPORT_PREFIXES = listOf(
            "kotlin.",
            "kotlinx.",
            "java.",
            "javax.",
            "io.github.oshai.",
            "io.miragon.bpmn.domain.",
        )
    }
}
