package com.nutritareas.app.data.docx

import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private sealed interface DocBlock {
    data class Title(val text: String) : DocBlock
    data class Heading1(val text: String) : DocBlock
    data class Heading2(val text: String) : DocBlock
    data class Bullet(val text: String) : DocBlock
    data class Paragraph(val text: String) : DocBlock
}

/**
 * Turns the assistant's lightweight-markdown final answer ("# ", "## ", "- " line prefixes, see
 * AssistantPersona) into a real, minimal .docx (OOXML) file that Word/Google Docs/LibreOffice
 * open as a normal editable document - built by hand with java.util.zip rather than a docx
 * library, since none of the common Java ones (Apache POI included) are a good fit for Android.
 */
object DocxGenerator {

    fun generate(assistantText: String, outputFile: File) {
        val blocks = parse(assistantText)
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(outputFile.outputStream()).use { zip ->
            writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeEntry(zip, "_rels/.rels", RELS_XML)
            writeEntry(zip, "docProps/core.xml", coreXml())
            writeEntry(zip, "docProps/app.xml", APP_XML)
            writeEntry(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS_XML)
            writeEntry(zip, "word/styles.xml", STYLES_XML)
            writeEntry(zip, "word/document.xml", documentXml(blocks))
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun parse(text: String): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                blocks += DocBlock.Paragraph(buffer.toString())
                buffer.clear()
            }
        }

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith("# ") -> {
                    flush()
                    blocks += DocBlock.Title(line.removePrefix("# ").trim())
                }
                line.startsWith("## ") -> {
                    flush()
                    blocks += DocBlock.Heading1(line.removePrefix("## ").trim())
                }
                line.startsWith("### ") -> {
                    flush()
                    blocks += DocBlock.Heading2(line.removePrefix("### ").trim())
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flush()
                    val marker = if (line.startsWith("- ")) "- " else "* "
                    blocks += DocBlock.Bullet(line.removePrefix(marker).trim())
                }
                else -> {
                    if (buffer.isNotEmpty()) buffer.append(' ')
                    buffer.append(line.trim())
                }
            }
        }
        flush()
        return blocks
    }

    private fun documentXml(blocks: List<DocBlock>): String {
        val body = StringBuilder()
        val effectiveBlocks = blocks.ifEmpty { listOf(DocBlock.Paragraph("")) }
        for (block in effectiveBlocks) {
            when (block) {
                is DocBlock.Title -> body.append(paragraphXml("Title", block.text))
                is DocBlock.Heading1 -> body.append(paragraphXml("Heading1", block.text))
                is DocBlock.Heading2 -> body.append(paragraphXml("Heading2", block.text))
                is DocBlock.Bullet -> body.append(paragraphXml("ListParagraph", "• " + block.text))
                is DocBlock.Paragraph -> body.append(paragraphXml(null, block.text))
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>$body" +
            "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/>" +
            "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/>" +
            "</w:sectPr></w:body></w:document>"
    }

    private fun paragraphXml(styleId: String?, text: String): String {
        val pPr = if (styleId != null) "<w:pPr><w:pStyle w:val=\"$styleId\"/></w:pPr>" else ""
        return "<w:p>$pPr<w:r><w:t xml:space=\"preserve\">${escapeXml(text)}</w:t></w:r></w:p>"
    }

    private fun escapeXml(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch.code in 0x00..0x08 || ch.code in 0x0B..0x0C || ch.code in 0x0E..0x1F -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun coreXml(): String {
        val now = Instant.now().toString()
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
            "<dc:title>Tareas</dc:title><dc:creator>Nutri-Tareas</dc:creator><cp:lastModifiedBy>Nutri-Tareas</cp:lastModifiedBy>" +
            "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$now</dcterms:created>" +
            "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">$now</dcterms:modified>" +
            "</cp:coreProperties>"
    }

    private const val CONTENT_TYPES_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
        "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
        "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>" +
        "</Types>"

    private const val RELS_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
        "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>" +
        "</Relationships>"

    private const val DOCUMENT_RELS_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    private const val APP_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">" +
        "<Application>Nutri-Tareas</Application></Properties>"

    private const val STYLES_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
        "<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/>" +
        "<w:pPr><w:spacing w:after=\"200\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/><w:qFormat/>" +
        "<w:pPr><w:spacing w:after=\"360\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"40\"/><w:szCs w:val=\"40\"/></w:rPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:qFormat/>" +
        "<w:pPr><w:spacing w:before=\"360\" w:after=\"160\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"30\"/><w:szCs w:val=\"30\"/></w:rPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/><w:basedOn w:val=\"Normal\"/><w:qFormat/>" +
        "<w:pPr><w:spacing w:before=\"280\" w:after=\"140\"/></w:pPr><w:rPr><w:b/><w:i/><w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/></w:rPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"ListParagraph\"><w:name w:val=\"List Paragraph\"/><w:basedOn w:val=\"Normal\"/><w:qFormat/>" +
        "<w:pPr><w:ind w:left=\"432\"/></w:pPr></w:style>" +
        "</w:styles>"
}
