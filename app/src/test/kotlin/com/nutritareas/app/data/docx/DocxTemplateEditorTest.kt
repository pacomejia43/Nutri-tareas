package com.nutritareas.app.data.docx

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxTemplateEditorTest {

    private val sampleDocumentXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:body>
        <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>Reporte de Actividad</w:t></w:r></w:p>
        <w:p><w:r><w:t>Materia: </w:t></w:r><w:r><w:t>Matemáticas</w:t></w:r></w:p>
        <w:p><w:r><w:t>Fecha: 01/01/2026</w:t></w:r></w:p>
        <w:p><w:r><w:t></w:t></w:r></w:p>
        <w:p/>
        <w:sectPr><w:pgSz w:w="12240" w:h="15840"/></w:sectPr>
        </w:body>
        </w:document>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private val baselineParagraphs = listOf("Reporte de Actividad", "Materia: Matemáticas", "Fecha: 01/01/2026", "", "")

    // --- DocxTemplateReader ---

    @Test
    fun `extractParagraphs concatenates every run's text within a paragraph`() {
        assertEquals(baselineParagraphs, DocxTemplateReader.extractParagraphs(sampleDocumentXml))
    }

    // --- DocxTemplateWriter: paragraph replacement ---

    @Test
    fun `replaceParagraphs rewrites only the targeted paragraph, leaving others untouched`() {
        val edited = DocxTemplateWriter.replaceParagraphs(sampleDocumentXml, mapOf(1 to "Materia: Historia"))

        assertEquals(
            listOf("Reporte de Actividad", "Materia: Historia", "Fecha: 01/01/2026", "", ""),
            DocxTemplateReader.extractParagraphs(edited),
        )
    }

    @Test
    fun `replaceParagraphs drops the old run's text so it can't linger in the output`() {
        val edited = DocxTemplateWriter.replaceParagraphs(sampleDocumentXml, mapOf(1 to "Materia: Historia"))

        val xml = String(edited, Charsets.UTF_8)
        assertTrue(xml.contains("Materia: Historia"))
        assertTrue("old run text must not linger", !xml.contains("Matemáticas"))
    }

    @Test
    fun `replaceParagraphs keeps the first run's formatting for a paragraph with rich text`() {
        val edited = DocxTemplateWriter.replaceParagraphs(sampleDocumentXml, mapOf(0 to "Nuevo Título"))

        val xml = String(edited, Charsets.UTF_8)
        assertTrue("bold formatting from the first run should survive", xml.contains("<w:b/>"))
        assertTrue("the pStyle on the paragraph itself should survive", xml.contains("w:val=\"Title\""))
        assertEquals(
            listOf("Nuevo Título", "Materia: Matemáticas", "Fecha: 01/01/2026", "", ""),
            DocxTemplateReader.extractParagraphs(edited),
        )
    }

    @Test
    fun `replaceParagraphs fills a paragraph whose only run has empty text`() {
        val edited = DocxTemplateWriter.replaceParagraphs(sampleDocumentXml, mapOf(3 to "Contenido nuevo"))

        assertEquals(
            listOf("Reporte de Actividad", "Materia: Matemáticas", "Fecha: 01/01/2026", "Contenido nuevo", ""),
            DocxTemplateReader.extractParagraphs(edited),
        )
    }

    @Test
    fun `replaceParagraphs adds a run to a paragraph that has none at all`() {
        val edited = DocxTemplateWriter.replaceParagraphs(sampleDocumentXml, mapOf(4 to "Firma: Paco"))

        assertEquals(
            listOf("Reporte de Actividad", "Materia: Matemáticas", "Fecha: 01/01/2026", "", "Firma: Paco"),
            DocxTemplateReader.extractParagraphs(edited),
        )
    }

    @Test
    fun `replaceParagraphs ignores an out-of-range index instead of throwing`() {
        val edited = DocxTemplateWriter.replaceParagraphs(sampleDocumentXml, mapOf(99 to "no debería aplicarse"))

        assertEquals(baselineParagraphs, DocxTemplateReader.extractParagraphs(edited))
    }

    @Test
    fun `replaceParagraphs applies multiple edits in one pass`() {
        val edited = DocxTemplateWriter.replaceParagraphs(
            sampleDocumentXml,
            mapOf(1 to "Materia: Historia", 2 to "Fecha: 15/03/2026", 3 to "Ensayo completo aquí."),
        )

        assertEquals(
            listOf("Reporte de Actividad", "Materia: Historia", "Fecha: 15/03/2026", "Ensayo completo aquí.", ""),
            DocxTemplateReader.extractParagraphs(edited),
        )
    }

    // --- DocxTemplateWriter.apply: full zip round trip ---

    @Test
    fun `apply preserves every other zip entry byte-for-byte and only rewrites document xml`() {
        val stylesXmlBytes = "<w:styles xmlns:w=\"...\">unchanged</w:styles>".toByteArray(Charsets.UTF_8)
        val template = DocxTemplate(
            fileName = "plantilla.docx",
            entries = linkedMapOf(
                "[Content_Types].xml" to "content-types".toByteArray(Charsets.UTF_8),
                "word/document.xml" to sampleDocumentXml,
                "word/styles.xml" to stylesXmlBytes,
            ),
            paragraphs = DocxTemplateReader.extractParagraphs(sampleDocumentXml),
        )
        val outputFile = File.createTempFile("docx-template-writer-test", ".docx")
        outputFile.deleteOnExit()

        DocxTemplateWriter.apply(template, mapOf(1 to "Materia: Historia"), outputFile)

        ZipFile(outputFile).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(setOf("[Content_Types].xml", "word/document.xml", "word/styles.xml"), names)

            val rewrittenStyles = zip.getInputStream(zip.getEntry("word/styles.xml")).readBytes()
            assertArrayEquals(stylesXmlBytes, rewrittenStyles)

            val rewrittenDocumentXml = zip.getInputStream(zip.getEntry("word/document.xml")).readBytes()
            assertEquals(
                listOf("Reporte de Actividad", "Materia: Historia", "Fecha: 01/01/2026", "", ""),
                DocxTemplateReader.extractParagraphs(rewrittenDocumentXml),
            )
        }
    }

    // --- parseTemplateEdits ---

    @Test
    fun `parseTemplateEdits with no markers returns an empty map`() {
        assertEquals(emptyMap<Int, String>(), parseTemplateEdits("Hola, aquí no hay nada que aplicar."))
    }

    @Test
    fun `parseTemplateEdits reads a single block trimmed`() {
        val text = "[[PARRAFO 1]]\n  Materia: Historia  \n"
        assertEquals(mapOf(1 to "Materia: Historia"), parseTemplateEdits(text))
    }

    @Test
    fun `parseTemplateEdits reads multiple blocks including multi-line content`() {
        val text = """
            [[PARRAFO 1]]
            Materia: Historia
            [[PARRAFO 3]]
            Primera línea del desarrollo.
            Segunda línea del desarrollo.
        """.trimIndent()

        val edits = parseTemplateEdits(text)

        assertEquals("Materia: Historia", edits[1])
        assertEquals("Primera línea del desarrollo.\nSegunda línea del desarrollo.", edits[3])
        assertEquals(2, edits.size)
    }

    @Test
    fun `parseTemplateEdits keeps the last block when the same index repeats`() {
        val text = "[[PARRAFO 2]]\nprimero\n[[PARRAFO 2]]\nsegundo"

        assertEquals(mapOf(2 to "segundo"), parseTemplateEdits(text))
    }
}
