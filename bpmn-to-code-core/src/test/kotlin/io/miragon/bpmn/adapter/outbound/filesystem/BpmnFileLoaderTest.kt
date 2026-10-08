package io.miragon.bpmn.adapter.outbound.filesystem

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BpmnFileLoaderTest {

    private val underTest = BpmnFileLoader()

    @Test
    fun `loadFrom returns empty list when no files match`(@TempDir tempDir: Path) {
        val result = underTest.loadFrom(tempDir.toString(), "*.bpmn")
        assertThat(result).isEmpty()
    }

    @Test
    fun `loadFrom returns matching files in base directory`(@TempDir tempDir: Path) {
        // given: a directory with files where some match the pattern
        Files.createFile(tempDir.resolve("process1.bpmn"))
        Files.createFile(tempDir.resolve("process2.bpmn"))
        Files.createFile(tempDir.resolve("other.txt"))

        // when: we call loadFrom with pattern "*.bpmn"
        val result = underTest.loadFrom(tempDir.toString(), "*.bpmn")

        // then: expect the list to contain only BPMN files
        assertThat(result).hasSize(2)
        assertThat(result.map { it.fileName }).containsExactlyInAnyOrder("process1.bpmn", "process2.bpmn")
    }

    @Test
    fun `loadFrom returns matching files from subdirectories`(@TempDir tempDir: Path) {
        // given: a base directory with a subdirectory containing a matching file
        val subDir = Files.createDirectory(tempDir.resolve("subDir"))
        val subSubDir = Files.createDirectory(subDir.resolve("subSubDir"))
        Files.createFile(subDir.resolve("diagram.bpmn"))
        Files.createFile(subSubDir.resolve("process.bpmn"))
        Files.createFile(tempDir.resolve("process.txt"))

        // when: we call loadFrom with pattern "**/*.bpmn"
        val result = underTest.loadFrom(tempDir.toString(), "**/*.bpmn")

        // then: expect the list to contain the matching subdirectory files, named by their path below the base directory
        assertThat(result).hasSize(2)
        assertThat(result.map { it.fileName }).containsExactlyInAnyOrder("subDir/diagram.bpmn", "subDir/subSubDir/process.bpmn")
    }

    @Test
    fun `loadFrom returns matching files from outside current root using relative paths`(@TempDir tempDir: Path) {
        // given: a directory structure with files outside the base directory
        val baseDir = Files.createDirectory(tempDir.resolve("project"))
        val externalDir = Files.createDirectory(tempDir.resolve("external"))
        Files.createFile(externalDir.resolve("external-process.bpmn"))
        Files.createFile(externalDir.resolve("other.txt"))

        // when: we call loadFrom with a relative path pattern going outside the root
        val result = underTest.loadFrom(baseDir.toString(), "../external/*.bpmn")

        // then: expect the list to contain the external BPMN file
        assertThat(result).hasSize(1)
        assertThat(result[0].fileName).isEqualTo("external-process.bpmn")
    }

    @Test
    fun `loadFrom returns matching files from external subdirectories using recursive wildcard`(@TempDir tempDir: Path) {
        // given: a directory structure with nested external files
        val baseDir = Files.createDirectory(tempDir.resolve("project"))
        val externalDir = Files.createDirectory(tempDir.resolve("external"))
        val subDir1 = Files.createDirectory(externalDir.resolve("subdir1"))
        val subDir2 = Files.createDirectory(externalDir.resolve("subdir2"))
        val deepDir = Files.createDirectory(subDir1.resolve("deep"))

        Files.createFile(externalDir.resolve("root-process.bpmn"))
        Files.createFile(subDir1.resolve("sub1-process.bpmn"))
        Files.createFile(subDir2.resolve("sub2-process.bpmn"))
        Files.createFile(deepDir.resolve("deep-process.bpmn"))
        Files.createFile(externalDir.resolve("other.txt"))
        Files.createFile(subDir1.resolve("readme.md"))

        // when: we call loadFrom with a recursive wildcard pattern
        val result = underTest.loadFrom(baseDir.toString(), "../external/**/*.bpmn")

        // then: expect the list to contain all BPMN files from external directory tree
        assertThat(result).hasSize(4)
        assertThat(result.map { it.fileName }).containsExactlyInAnyOrder(
            "root-process.bpmn",
            "subdir1/sub1-process.bpmn",
            "subdir2/sub2-process.bpmn",
            "subdir1/deep/deep-process.bpmn",
        )
    }

    @Test
    fun `loadFrom returns matching files from deeply nested external paths`(@TempDir tempDir: Path) {
        // given: a directory structure with deeply nested external files
        val projectDir = Files.createDirectory(tempDir.resolve("workspace"))
        val baseDir = Files.createDirectory(projectDir.resolve("current"))
        val deepDir = Files.createDirectory(tempDir.resolve("shared"))
        val nestedDir = Files.createDirectory(deepDir.resolve("resources"))
        val bpmnDir = Files.createDirectory(nestedDir.resolve("bpmn"))

        Files.createFile(bpmnDir.resolve("shared-process.bpmn"))
        Files.createFile(nestedDir.resolve("resource-process.bpmn"))
        Files.createFile(bpmnDir.resolve("config.xml"))

        // when: we call loadFrom with a deep relative path pattern
        val result = underTest.loadFrom(baseDir.toString(), "../../shared/**/*.bpmn")

        // then: expect the list to contain all BPMN files from the deep external path
        assertThat(result).hasSize(2)
        assertThat(result.map { it.fileName }).containsExactlyInAnyOrder("resources/bpmn/shared-process.bpmn", "resources/resource-process.bpmn")
    }

    @Test
    fun `loadFrom returns specific external file when exact path is provided`(@TempDir tempDir: Path) {
        // given: a directory structure with specific external files
        val baseDir = Files.createDirectory(tempDir.resolve("project"))
        val externalDir = Files.createDirectory(tempDir.resolve("external"))
        val specificDir = Files.createDirectory(externalDir.resolve("specific"))

        Files.createFile(specificDir.resolve("target.bpmn"))
        Files.createFile(specificDir.resolve("other.bpmn"))
        Files.createFile(externalDir.resolve("different.bpmn"))

        // when: we call loadFrom with a specific file pattern
        val result = underTest.loadFrom(baseDir.toString(), "../external/specific/target.bpmn")

        // then: expect the list to contain only the specific target file
        assertThat(result).hasSize(1)
        assertThat(result[0].fileName).isEqualTo("target.bpmn")
    }

    @Test
    fun `loadFrom returns files ordered by relative path independent of filesystem order`(@TempDir tempDir: Path) {
        // given: files that share a file name across sibling directories, plus siblings whose
        // relative-path order differs from their file-name order
        val folders = listOf("staging", "dev", "test", "prod")
        folders.forEach { folder ->
            val dir = Files.createDirectory(tempDir.resolve(folder))
            Files.write(dir.resolve("order-process.bpmn"), "$folder/order-process.bpmn".toByteArray())
        }

        // when: we load all of them
        val result = underTest.loadFrom(tempDir.toString(), "**/*.bpmn")

        // then: they come back sorted by their relative path (readdir order cannot be forced, so we
        // assert the invariant, not a specific shuffle), which is also the name that tells them apart
        val expectedPaths = listOf(
            "dev/order-process.bpmn",
            "prod/order-process.bpmn",
            "staging/order-process.bpmn",
            "test/order-process.bpmn",
        )
        assertThat(result.map { String(it.content) }).isEqualTo(expectedPaths)
        assertThat(result.map { it.fileName }).isEqualTo(expectedPaths)
    }

    @Test
    fun `isBpmnFileToLoad accepts exactly the files loadFrom reads`(@TempDir tempDir: Path) {
        // given: BPMN files in the search directory, in a subdirectory and behind a symlinked directory
        val resources = Files.createDirectories(tempDir.resolve("project/src/main/resources"))
        val nested = Files.createDirectory(resources.resolve("nested"))
        val external = Files.createDirectory(tempDir.resolve("external"))
        Files.createSymbolicLink(resources.resolve("linked"), external)
        Files.createFile(resources.resolve("order.bpmn"))
        Files.createFile(resources.resolve("notes.txt"))
        Files.createFile(nested.resolve("payment.bpmn"))
        Files.createFile(external.resolve("shipping.bpmn"))
        val candidates = listOf(
            "order.bpmn",
            "notes.txt",
            "nested/payment.bpmn",
            "linked/shipping.bpmn",
        )
        val baseDirectory = tempDir.resolve("project").toString()
        val filePattern = "src/main/resources/**/*.bpmn"

        // when: asking for each candidate and loading the files
        val accepted = candidates.filter { candidate ->
            underTest.isBpmnFileToLoad(baseDirectory = baseDirectory, filePattern = filePattern, pathInSearchDirectory = candidate)
        }
        val loaded = underTest.loadFrom(baseDirectory, filePattern)

        // then: both agree, and the file behind the symlinked directory is left out
        assertThat(underTest.resolveSearchDirectory(baseDirectory, filePattern)).isEqualTo(resources)
        assertThat(accepted).containsExactly("order.bpmn", "nested/payment.bpmn")
        assertThat(loaded.map { it.fileName }).containsExactlyInAnyOrder("order.bpmn", "nested/payment.bpmn")
    }
}
