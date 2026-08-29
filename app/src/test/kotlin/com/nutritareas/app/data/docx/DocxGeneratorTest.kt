package com.nutritareas.app.data.docx

import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class DocxGeneratorTest {

    private val requiredEntries = setOf(
        "[Content_Types].xml",
        "_rels/.rels",
        "docProps/core.xml",
        "docProps/app.xml",
        "word/_rels/document.xml.rels",
        "word/styles.xml",
        "word/document.xml",
    )

    @Test
    fun `generate writes a zip with every required OOXML part, each well-formed XML`() {
        val file = generateToTempFile("# Título\n\nUn párrafo.")

        ZipFile(file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(requiredEntries, names)
            for (entry in zip.entries()) {
                val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                // Must not throw: every part is well-formed XML.
                builder.parse(zip.getInputStream(entry))
            }
        }
    }

    @Test
    fun `document body maps lightweight-markdown prefixes to the right paragraph styles`() {
        val input = """
            # Tareas de Nutrición

            ## Tarea 1: Cálculo
            Primer párrafo con dos oraciones. Sigue aquí.

            - Punto uno
            - Punto dos

            ### Nota
            Texto final.
        """.trimIndent()

        val paragraphs = documentParagraphs(generateToTempFile(input))

        assertEquals(
            listOf(
                "Title" to "Tareas de Nutrición",
                "Heading1" to "Tarea 1: Cálculo",
                null to "Primer párrafo con dos oraciones. Sigue aquí.",
                "ListParagraph" to "• Punto uno",
                "ListParagraph" to "• Punto dos",
                "Heading2" to "Nota",
                null to "Texto final.",
            ),
            paragraphs,
        )
    }

    @Test
    fun `xml special characters round-trip through escaping`() {
        val paragraphs = documentParagraphs(generateToTempFile("Datos: 70 kg & talla < 1.80 m, IMC > 20."))
        assertEquals(listOf(null to "Datos: 70 kg & talla < 1.80 m, IMC > 20."), paragraphs)
    }

    @Test
    fun `blank input still produces a single valid empty paragraph`() {
        val paragraphs = documentParagraphs(generateToTempFile(""))
        assertEquals(listOf(null to ""), paragraphs)
    }

    /** Returns each body paragraph as (styleId or null for Normal) to its concatenated text. */
    private fun documentParagraphs(file: File): List<Pair<String?, String>> {
        val document = parseEntry(file, "word/document.xml")
        val paragraphNodes = document.getElementsByTagName("w:p")
        return (0 until paragraphNodes.length).map { i ->
            val p = paragraphNodes.item(i) as Element
            val styleNodes = p.getElementsByTagName("w:pStyle")
            val styleId = if (styleNodes.length > 0) (styleNodes.item(0) as Element).getAttribute("w:val") else null
            val textNodes = p.getElementsByTagName("w:t")
            val text = (0 until textNodes.length).joinToString("") { textNodes.item(it).textContent }
            styleId to text
        }
    }

    private fun parseEntry(file: File, entryName: String): Document =
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryName) ?: error("missing entry $entryName")
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(zip.getInputStream(entry))
        }

    private fun generateToTempFile(text: String): File {
        val file = File.createTempFile("docx-generator-test", ".docx")
        file.deleteOnExit()
        DocxGenerator.generate(text, file)
        assertTrue(file.length() > 0)
        return file
    }
}
