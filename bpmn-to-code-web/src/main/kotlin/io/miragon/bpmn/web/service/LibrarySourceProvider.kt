package io.miragon.bpmn.web.service

import io.miragon.bpmn.web.model.GenerateResponse
import io.miragon.bpmn.web.model.GenerateResponse.Snippet
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads the hand-written sources of each runtime library that the web module bundles as resources (copied in via
 * `copyLibrarySources` during the build). The playground exposes these so users can preview the shared types alongside
 * their generated Process API and, if they opt in, include them in the ZIP download.
 */
class LibrarySourceProvider {

    private val cache = ConcurrentHashMap<RuntimeLibrary, List<GenerateResponse.GeneratedFile>>()
    private val version: String by lazy { loadProjectVersion() }

    fun libraryFiles(library: RuntimeLibrary): List<GenerateResponse.GeneratedFile> = cache.computeIfAbsent(library) { loadLibraryFiles(it) }

    fun runtimeDependency(library: RuntimeLibrary): GenerateResponse.RuntimeDependency {
        val currentVersion = version
        return GenerateResponse.RuntimeDependency(
            name = library.packageName,
            version = currentVersion,
            importedFrom = library.importedFrom,
            sourcesDirectory = library.sourcesDirectory,
            snippets = snippetsOf(library, currentVersion),
        )
    }

    private fun snippetsOf(library: RuntimeLibrary, version: String): List<Snippet> = when (library) {
        RuntimeLibrary.JVM -> listOf(
            Snippet(label = "Gradle", code = "implementation(\"${library.packageName}:$version\")"),
            Snippet(
                label = "Maven",
                code = """
                    |<dependency>
                    |    <groupId>io.miragon</groupId>
                    |    <artifactId>bpmn-to-code-runtime</artifactId>
                    |    <version>$version</version>
                    |</dependency>
                """.trimMargin(),
            ),
        )

        RuntimeLibrary.CSHARP -> listOf(
            Snippet(label = ".NET CLI", code = "dotnet add package ${library.packageName} --version $version"),
            Snippet(label = "PackageReference", code = "<PackageReference Include=\"${library.packageName}\" Version=\"$version\" />"),
        )
    }

    private fun loadLibraryFiles(library: RuntimeLibrary): List<GenerateResponse.GeneratedFile> {
        val directory = "library-sources/${library.resourceDirectory}"
        val manifest = readResource("$directory/manifest.txt") ?: return emptyList()
        val fileNames = manifest.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return fileNames.mapNotNull { name ->
            val content = readResource("$directory/$name") ?: return@mapNotNull null
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
