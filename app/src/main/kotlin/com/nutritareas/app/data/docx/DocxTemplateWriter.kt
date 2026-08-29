package com.nutritareas.app.data.docx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

/**
 * Applies text edits to specific body paragraphs of a [DocxTemplate] and repackages it as a new
 * .docx. Every zip entry other than word/document.xml is copied through byte-for-byte, and only
 * the targeted paragraphs' text changes - their own formatting (from the first run kept) and
 * everything else in the template (styles, theme, fonts, media, headers/footers) is untouched.
 */
object DocxTemplateWriter {

    /** [replacements] maps a paragraph index (as seen in [DocxTemplate.paragraphs]) to its new text. */
    fun apply(template: DocxTemplate, replacements: Map<Int, String>, outputFile: File) {
        val documentXml = template.entries["word/document.xml"]
            ?: throw DocxTemplateException("La plantilla no tiene word/document.xml.")
        val editedXml = replaceParagraphs(documentXml, replacements)

        outputFile.parentFile?.mkdirs()
        ZipOutputStream(outputFile.outputStream()).use { zip ->
            for ((name, bytes) in template.entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(if (name == "word/document.xml") editedXml else bytes)
                zip.closeEntry()
            }
        }
    }

    internal fun replaceParagraphs(documentXml: ByteArray, replacements: Map<Int, String>): ByteArray {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(documentXml))
        val paragraphNodes = document.getElementsByTagName("w:p")

        for ((index, newText) in replacements) {
            if (index !in 0 until paragraphNodes.length) continue
            setParagraphText(document, paragraphNodes.item(index) as Element, newText)
        }

        val output = ByteArrayOutputStream()
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.transform(DOMSource(document), StreamResult(output))
        return output.toByteArray()
    }

    /** Keeps the paragraph's first run (and its `<w:rPr>` formatting) for the new text; drops any other runs. */
    private fun setParagraphText(document: Document, paragraph: Element, newText: String) {
        val runs = paragraph.getElementsByTagName("w:r").toElementList()
        if (runs.isEmpty()) {
            paragraph.appendChild(newRun(document, newText))
            return
        }

        val firstRun = runs.first()
        runs.drop(1).forEach { it.parentNode.removeChild(it) }
        firstRun.getElementsByTagName("w:t").toNodeList().forEach { it.parentNode.removeChild(it) }
        firstRun.appendChild(newTextElement(document, newText))
    }

    private fun newRun(document: Document, text: String): Element =
        document.createElement("w:r").apply { appendChild(newTextElement(document, text)) }

    private fun newTextElement(document: Document, text: String): Element =
        document.createElement("w:t").apply {
            setAttribute("xml:space", "preserve")
            textContent = text
        }

    /** Materializes a live [NodeList] into a plain list first, so removing items while iterating is safe. */
    private fun NodeList.toElementList(): List<Element> = (0 until length).map { item(it) as Element }

    private fun NodeList.toNodeList(): List<Node> = (0 until length).map { item(it) }
}
