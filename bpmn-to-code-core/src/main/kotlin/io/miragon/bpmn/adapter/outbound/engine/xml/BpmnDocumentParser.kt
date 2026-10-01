package io.miragon.bpmn.adapter.outbound.engine.xml

import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants
import org.camunda.bpm.model.bpmn.impl.BpmnParser
import org.camunda.bpm.model.xml.ModelValidationException
import org.camunda.bpm.model.xml.impl.util.DomUtil
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.dom.DOMSource

/**
 * Reads a BPMN model like Camunda's `Bpmn.readModelFromStream`, including the validation against the BPMN 2.0
 * schema, at a fraction of the cost: Camunda loads the schema anew for every document and validates it twice, once
 * while building the DOM and once before creating the model. This parser builds the DOM without validation and then
 * validates it once against the schema Camunda compiled at startup.
 *
 * The validation augments the DOM in place, so it ends up exactly as Camunda's validating parse leaves it: with the
 * schema's attribute defaults, normalised values, and `id` attributes that references resolve against.
 */
internal object BpmnDocumentParser : BpmnParser() {

    private val bpmnSchema = schemas.getValue(BpmnModelConstants.BPMN20_NS)

    private val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }

    fun parse(stream: InputStream): BpmnModelInstance {
        val document = synchronized(documentBuilderFactory) {
            DomUtil.parseInputStream(documentBuilderFactory, stream)
        }
        val root = document.domSource.node
        try {
            bpmnSchema.newValidator().validate(DOMSource(root), DOMResult(root))
        } catch (e: SAXException) {
            throw ModelValidationException("DOM document is not valid", e)
        } catch (e: IOException) {
            throw ModelValidationException("Error during DOM document validation", e)
        }
        return createModelInstance(document)
    }
}
