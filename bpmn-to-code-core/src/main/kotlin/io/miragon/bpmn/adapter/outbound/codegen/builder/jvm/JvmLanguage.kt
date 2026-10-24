package io.miragon.bpmn.adapter.outbound.codegen.builder.jvm

import io.miragon.bpmn.adapter.outbound.codegen.PackagePath

/**
 * What tells generated Kotlin and Java apart: the templates under `templates/<templateDirectory>`, how names and
 * literals are written, which types need no import, and how a constant and code are written in a doc comment.
 * A type named like one of the generated types is written fully qualified: next to a flow node `Event`, the runtime
 * interface is `io.miragon.bpmn.runtime.Event`.
 */
internal enum class JvmLanguage(
    val templateDirectory: String,
    val syntax: JvmSyntax,
    private val qualifiedNames: Map<String, String>,
    private val implicitPackages: Set<String>,
    private val packageNameOf: (PackagePath) -> String,
    private val codeSpanOf: (String) -> String,
    private val constantDeclaration: String,
) {
    KOTLIN(
        templateDirectory = "kotlin",
        syntax = KotlinSyntax,
        qualifiedNames = JvmRuntimeTypes.qualifiedNames(listOf("RegisteredVariableDefinitions")) + mapOf(
            "Boolean" to "kotlin.Boolean",
            "String" to "kotlin.String",
            "List" to "kotlin.collections.List",
        ),
        implicitPackages = setOf("kotlin", "kotlin.collections"),
        packageNameOf = { packagePath -> packagePath.value.split('.').joinToString(".") { KotlinSyntax.identifier(it) } },
        codeSpanOf = { code -> "`$code`" },
        constantDeclaration = "const val String",
    ),
    JAVA(
        templateDirectory = "java",
        syntax = JavaSyntax,
        qualifiedNames = JvmRuntimeTypes.qualifiedNames(listOf("VariableDefinitions")) + mapOf(
            "List" to "java.util.List",
            "String" to "java.lang.String",
            "Override" to "java.lang.Override",
        ),
        implicitPackages = setOf("java.lang"),
        packageNameOf = { packagePath -> packagePath.value },
        codeSpanOf = { code -> "{@code $code}" },
        constantDeclaration = "public static final String",
    ),
    ;

    fun sourceFile(packagePath: PackagePath, generatedTypeNames: Set<String>): ImportingSourceFile {
        val types = TypeReferences(
            qualifiedNames = qualifiedNames,
            implicitPackages = implicitPackages,
            shadowingNames = generatedTypeNames,
        )
        return ImportingSourceFile(
            wrapperTemplate = "$templateDirectory/source-file.ftl",
            packageName = packageNameOf(packagePath),
            types = types,
        )
    }

    fun codeSpan(code: String): String = codeSpanOf(code)

    /**
     * Why job types and variable names are plain strings rather than runtime types.
     */
    fun constantsForAnnotations(): String {
        val declaration = codeSpan(constantDeclaration)
        return "Kept as $declaration because annotation arguments must be compile-time constants."
    }
}
