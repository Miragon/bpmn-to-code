package io.miragon.bpmn.adapter.outbound.codegen.freemarker

import freemarker.cache.ClassTemplateLoader
import freemarker.cache.StrongCacheStorage
import freemarker.core.PlainTextOutputFormat
import freemarker.core.TemplateClassResolver
import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import io.miragon.bpmn.domain.GeneratedFileHeader
import java.io.StringWriter
import java.util.Locale
import java.util.TimeZone

/**
 * Renders the FreeMarker templates under `io/miragon/bpmn/codegen/templates` with a template model of literals,
 * identifiers and references; the templates write the code around them. The configuration is fixed, so the same model
 * renders to the same bytes on every machine, and parsed templates are kept for the lifetime of the class loader.
 */
internal object TemplateRenderer {

    private const val TEMPLATE_ROOT = "/io/miragon/bpmn/codegen/templates"

    private val configuration = Configuration(Configuration.VERSION_2_3_34).apply {
        templateLoader = ClassTemplateLoader(TemplateRenderer::class.java, TEMPLATE_ROOT)
        cacheStorage = StrongCacheStorage()
        templateUpdateDelayMilliseconds = Long.MAX_VALUE
        localizedLookup = false
        defaultEncoding = Charsets.UTF_8.name()
        outputEncoding = Charsets.UTF_8.name()
        locale = Locale.ROOT
        timeZone = TimeZone.getTimeZone("UTC")
        numberFormat = "computer"
        booleanFormat = "c"
        outputFormat = PlainTextOutputFormat.INSTANCE
        interpolationSyntax = Configuration.DOLLAR_INTERPOLATION_SYNTAX
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        fallbackOnNullLoopVariable = false
        newBuiltinClassResolver = TemplateClassResolver.ALLOWS_NOTHING_RESOLVER
        isAPIBuiltinEnabled = false
        setSharedVariable("generatedFileHeader", GeneratedFileHeader.LINE)
    }

    fun render(templateName: String, model: Any): String {
        val output = StringWriter()
        configuration.getTemplate(templateName).process(model, output)
        return output.toString()
    }
}
