package io.miragon.bpmn.adapter.outbound.engine.xml

import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/**
 * A DOCTYPE declaration is how XXE attacks reach an XML parser, so this wrapper rejects any file that
 * contains one with a [SecurityException] naming the reason, before [BpmnDocumentParser] reads it.
 */
internal object SecureBpmnParser {

    private const val DISALLOW_DOCTYPE_FEATURE = "http://apache.org/xml/features/disallow-doctype-decl"

    private val saxFactory = SAXParserFactory.newInstance().also { factory ->
        factory.setFeature(DISALLOW_DOCTYPE_FEATURE, true)
    }

    fun readModelFromBytes(bytes: ByteArray): BpmnModelInstance {
        val stream = bytes.inputStream()
        rejectDoctypeDeclaration(stream)
        stream.reset()
        return BpmnDocumentParser.parse(stream)
    }

    private fun rejectDoctypeDeclaration(stream: InputStream) {
        try {
            saxFactory.newSAXParser().parse(
                stream,
                object : DefaultHandler() {
                    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                        // Abort as soon as we reach the first element — no DOCTYPE was encountered
                        throw EarlyAbortException()
                    }
                },
            )
        } catch (_: EarlyAbortException) {
            return // clean exit — no DOCTYPE found
        } catch (e: SAXParseException) {
            // The same exception type covers both the security check firing and ordinary malformed XML.
            // Only the former is a security problem; calling a truncated file a DOCTYPE violation sends
            // the reader looking for something that is not there.
            if (e.message?.contains(DISALLOW_DOCTYPE_FEATURE) == true) {
                throw SecurityException("DOCTYPE declarations are not allowed in BPMN files", e)
            }
            throw IllegalArgumentException("File is not well-formed XML: ${e.message}", e)
        }
    }

    private class EarlyAbortException : SAXException()
}
