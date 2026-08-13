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
        productionFiles()
            .assertTrue(testName = "files should declare at most one top-level type to follow SRP") { file ->
                file.classesAndInterfacesAndObjects(includeNested = false, includeLocal = false).size <= 1
            }
    }

    /**
     * A file that declares a type declares *only* that type: helpers belong inside it (or in their own
     * file), not beside it. Without this, a class file slowly accumulates free functions that no reader
     * expects to find there — which is how `ProcessModel.kt` grew a second, unrelated half.
     *
     * Files holding only top-level functions — a named collection of helpers with no type of its own —
     * stay allowed.
     */
    @Test
    fun `a file declaring a type declares nothing else at top level`() {
        productionFiles()
            .filter { it.classesAndInterfacesAndObjects(includeNested = false, includeLocal = false).isNotEmpty() }
            .assertTrue(testName = "a type's file should not also declare top-level functions or properties") { file ->
                val functions = file.functions(includeNested = false, includeLocal = false)
                val properties = file.properties(includeNested = false)
                functions.isEmpty() && properties.isEmpty()
            }
    }

    /**
     * Project sources only — never the gitignored `bin/` output an IDE may leave behind.
     */
    private fun productionFiles() = Konsist
        .scopeFromProject()
        .files
        .filter { it.path.contains("/src/") }
}
