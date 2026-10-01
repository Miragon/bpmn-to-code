package io.miragon.bpmn.benchmark

import java.lang.reflect.Method

/**
 * The bpmn-to-code generator on the classpath, reached reflectively through the in-memory entry point the web module
 * uses. Its signature differs slightly between releases, so this is what lets one harness measure the current build
 * and a published baseline alike.
 */
class Generator {

    private val pluginType = Class.forName("$INBOUND.CreateProcessApiInMemoryPlugin")
    private val plugin = newInstance(pluginType)
    private val execute = pluginType.methods.filter { it.name == "execute" }.maxBy { it.parameterCount }
    private val bpmnInput = Class.forName("$INBOUND.CreateProcessApiInMemoryPlugin\$BpmnInput")
        .getConstructor(String::class.java, String::class.java)
    private val validationConfig = newInstance(Class.forName("$DOMAIN.validation.model.ValidationConfig"))

    private val extractorType = Class.forName("io.miragon.bpmn.adapter.outbound.engine.ExtractBpmnAdapter")
    private val extractor = newInstance(extractorType)
    private val extract = extractorType.methods.first { it.name == "extract" && !it.isBridge }
    private val bpmnResource = Class.forName("$DOMAIN.BpmnResource")
        .getConstructor(String::class.java, ByteArray::class.java)

    private val generatedFileType = Class.forName("$DOMAIN.GeneratedApiFile")
    private val fileName = generatedFileType.getMethod("getFileName")
    private val packagePath = generatedFileType.getMethod("getPackagePath")
    private val content = generatedFileType.getMethod("getContent")

    fun generate(scenario: Scenario, language: String): List<GeneratedSource> {
        val inputs = scenario.models.map { bpmnInput.newInstance(it.xml, it.name) }
        val outputLanguage = enumConstant(method = execute, parameterIndex = 2, name = language)
        val engine = enumConstant(method = execute, parameterIndex = 3, name = scenario.engine)
        val files = if (execute.parameterCount == PARAMETERS_WITH_VARIANTS) {
            execute.invoke(plugin, inputs, PACKAGE_PATH, outputLanguage, engine, validationConfig, false)
        } else {
            execute.invoke(plugin, inputs, PACKAGE_PATH, outputLanguage, engine, validationConfig)
        }
        return (files as List<*>).map { toSource(it) }
    }

    /**
     * Only reads the BPMN files, which is the part of [generate] before validation and code generation.
     */
    fun parse(scenario: Scenario) {
        val engine = enumConstant(method = extract, parameterIndex = 1, name = scenario.engine)
        scenario.models.forEach { model ->
            val resource = bpmnResource.newInstance(model.name, model.xml.encodeToByteArray())
            extract.invoke(extractor, resource, engine)
        }
    }

    private fun newInstance(type: Class<*>): Any = type.getConstructor().newInstance()

    private fun toSource(file: Any?): GeneratedSource = GeneratedSource(
        fileName = fileName.invoke(file) as String,
        packagePath = packagePath.invoke(file) as String,
        content = content.invoke(file) as String,
    )

    private fun enumConstant(method: Method, parameterIndex: Int, name: String): Any {
        val enumType = method.parameterTypes[parameterIndex]
        return enumType.enumConstants.first { (it as Enum<*>).name == name }
    }

    data class GeneratedSource(val fileName: String, val packagePath: String, val content: String) {

        val lineCount: Int
            get() = content.lines().size
    }

    companion object {
        val LANGUAGES = listOf("KOTLIN", "JAVA")

        private const val INBOUND = "io.miragon.bpmn.adapter.inbound"
        private const val DOMAIN = "io.miragon.bpmn.domain"
        private const val PACKAGE_PATH = "com.example.process"
        private const val PARAMETERS_WITH_VARIANTS = 6
    }
}
