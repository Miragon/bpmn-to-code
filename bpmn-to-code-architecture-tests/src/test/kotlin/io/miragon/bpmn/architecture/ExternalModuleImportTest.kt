package io.miragon.bpmn.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Enforces that plugin wrapper modules only import from:
 * - `io.miragon.bpmn.domain.*` — domain objects
 * - `io.miragon.bpmn.adapter.inbound.*` — inbound adapters (the public API surface)
 *
 * Runs once per plugin module (Gradle, Maven, Web). `bpmn-to-code-testing` is intentionally
 * excluded — it is a BPMN parsing utility library that legitimately uses the BPMN extraction
 * out-adapter directly to implement its validation logic.
 */
class ExternalModuleImportTest {

    private val forbiddenImportPrefixes = listOf(
        "io.miragon.bpmn.application.",      // services and ports
        "io.miragon.bpmn.adapter.outbound.", // out-adapters
    )

    @ParameterizedTest(name = "{0} only imports domain objects or inbound adapters from core")
    @ValueSource(
        strings = [
            "bpmn-to-code-gradle",
            "bpmn-to-code-maven",
            "bpmn-to-code-web",
        ]
    )
    fun `plugin module only imports domain objects or inbound adapters from core`(modulePath: String) {
        Konsist
            .scopeFromProject()
            .files
            .filter { file -> file.path.contains(modulePath) }
            .assertTrue { file ->
                file.imports.none { import ->
                    forbiddenImportPrefixes.any { import.name.startsWith(it) }
                }
            }
    }
}
