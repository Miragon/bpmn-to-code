package io.miragon.bpmn.web.service

import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.web.model.GenerateResponse
import java.util.Properties

/**
 * Loads the hand-written `bpmn-to-code-runtime` Kotlin sources that the web module bundles as resources
 * (copied in via `copyLibrarySources` during the build). The playground exposes these so users can
 * preview the shared types alongside their generated Process API and, if they opt in, include them
 * in the ZIP download for a self-contained bundle. It also names the runtime package of each output language.
 */
class LibrarySourceProvider {

    private val cache: List<GenerateResponse.GeneratedFile> by lazy { loadLibraryFiles() }
    private val version: String by lazy { loadProjectVersion() }

    fun libraryFiles(): List<GenerateResponse.GeneratedFile> = cache

    fun runtimeDependency(language: OutputLanguage): GenerateResponse.RuntimeDependency = when (language) {
        OutputLanguage.KOTLIN, OutputLanguage.JAVA -> jvmRuntimeDependency()
        OutputLanguage.CSHARP -> csharpRuntimeDependency()
        OutputLanguage.TYPESCRIPT -> typescriptRuntimeDependency()
    }

    private fun jvmRuntimeDependency(): GenerateResponse.RuntimeDependency {
        val coordinate = "io.miragon:bpmn-to-code-runtime"
        val gradle = GenerateResponse.Snippet(label = "Gradle", code = "implementation(\"$coordinate:$version\")")
        val maven = GenerateResponse.Snippet(
            label = "Maven",
            code = """
                |<dependency>
                |    <groupId>io.miragon</groupId>
                |    <artifactId>bpmn-to-code-runtime</artifactId>
                |    <version>$version</version>
                |</dependency>
            """.trimMargin(),
        )
        return GenerateResponse.RuntimeDependency(
            name = coordinate,
            importedFrom = "io.miragon.bpmn.runtime",
            version = version,
            snippets = listOf(gradle, maven),
        )
    }

    private fun csharpRuntimeDependency(): GenerateResponse.RuntimeDependency {
        val packageId = "Miragon.BpmnToCode.Runtime"
        val projectFile = GenerateResponse.Snippet(
            label = ".csproj",
            code = "<PackageReference Include=\"$packageId\" Version=\"$version\" />",
        )
        val cli = GenerateResponse.Snippet(
            label = ".NET CLI",
            code = "dotnet add package $packageId --version $version",
        )
        return GenerateResponse.RuntimeDependency(
            name = packageId,
            importedFrom = packageId,
            version = version,
            snippets = listOf(projectFile, cli),
        )
    }

    private fun typescriptRuntimeDependency(): GenerateResponse.RuntimeDependency {
        val packageName = "@miragon/bpmn-to-code-runtime"
        val npm = GenerateResponse.Snippet(label = "npm", code = "npm install --save-exact $packageName@$version")
        return GenerateResponse.RuntimeDependency(
            name = packageName,
            importedFrom = packageName,
            version = version,
            snippets = listOf(npm),
        )
    }

    private fun loadLibraryFiles(): List<GenerateResponse.GeneratedFile> {
        val manifest = readResource("library-sources/manifest.txt") ?: return emptyList()
        val fileNames = manifest.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return fileNames.mapNotNull { name ->
            val content = readResource("library-sources/$name") ?: return@mapNotNull null
            GenerateResponse.GeneratedFile(fileName = name, content = content, processId = "bpmn-to-code-runtime")
        }
    }

    private fun loadProjectVersion(): String {
        val stream = javaClass.classLoader.getResourceAsStream("version.properties") ?: return "unknown"
        val properties = stream.use {
            Properties().apply { load(it) }
        }
        return properties.getProperty("version")?.takeIf { it.isNotBlank() } ?: "unknown"
    }

    private fun readResource(path: String): String? {
        val stream = javaClass.classLoader.getResourceAsStream(path) ?: return null
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
