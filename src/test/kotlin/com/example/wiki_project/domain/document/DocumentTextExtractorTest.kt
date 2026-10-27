package com.example.wiki_project.domain.document

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.document.infrastructure.extractor.DocumentTextExtractor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class DocumentTextExtractorTest {

    private val extractor = DocumentTextExtractor()

    @Test
    @DisplayName("UTF-8 마크다운 문서는 BOM을 제거한 원문 텍스트를 그대로 반환한다")
    fun extractUtf8Markdown() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "# 배포 가이드\n매주 목요일 배포".toByteArray()

        assertThat(extractor.extract("MD", bytes)).isEqualTo("# 배포 가이드\n매주 목요일 배포")
    }

    @Test
    @DisplayName("Windows 메모장(MS949)으로 저장된 한글 TXT도 깨지지 않고 추출된다")
    fun extractMs949Text() {
        val bytes = "사내 보안 규정".toByteArray(Charset.forName("MS949"))

        assertThat(extractor.extract("TXT", bytes)).isEqualTo("사내 보안 규정")
    }

    @Test
    @DisplayName("PDF 문서의 본문 텍스트를 추출한다")
    fun extractPdf() {
        val pdfBytes = PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Deployment runs every Thursday")
                stream.endText()
            }
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

        assertThat(extractor.extract("PDF", pdfBytes)).contains("Deployment runs every Thursday")
    }

    @Test
    @DisplayName("DOCX 문서의 문단 텍스트를 추출한다")
    fun extractDocx() {
        val docxBytes = XWPFDocument().use { document ->
            document.createParagraph().createRun().setText("회의록: 다음 배포는 목요일")
            ByteArrayOutputStream().also { document.write(it) }.toByteArray()
        }

        assertThat(extractor.extract("DOCX", docxBytes)).contains("회의록: 다음 배포는 목요일")
    }

    @Test
    @DisplayName("텍스트가 없는 PDF(이미지 스캔본 등)는 추출 실패 예외를 던진다")
    fun failOnEmptyPdf() {
        val emptyPdf = PDDocument().use { document ->
            document.addPage(PDPage())
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

        val exception = assertThrows<BusinessException> { extractor.extract("PDF", emptyPdf) }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.DOCUMENT_TEXT_EXTRACTION_FAILED)
    }

    @Test
    @DisplayName("확장자는 DOCX지만 실제로는 손상된 파일이면 추출 실패 예외를 던진다")
    fun failOnCorruptedDocx() {
        val exception = assertThrows<BusinessException> { extractor.extract("DOCX", "not a docx".toByteArray()) }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.DOCUMENT_TEXT_EXTRACTION_FAILED)
    }
}
