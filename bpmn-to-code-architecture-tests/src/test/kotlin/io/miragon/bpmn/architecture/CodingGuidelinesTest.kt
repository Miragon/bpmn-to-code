package io.miragon.bpmn.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/**
 * General Kotlin coding guidelines that apply project-wide, independent of the hexagonal layering
 * (which lives in [HexagonalArchitectureTest]). These are source-structure rules Konsist can see but
 * the compiler erases — the reason Konsist earns its place here.
 */
class CodingGuidelinesTest {

    @Test
    fun `each source file declares at most one top-level type`() {
        Konsist
            .scopeFromProject()
            .files
            .assertTrue(testName = "files should declare at most one top-level type to follow SRP") { file ->
                file.classesAndInterfacesAndObjects(includeNested = false, includeLocal = false).size <= 1
            }
    }
}
