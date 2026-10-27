package com.example.wiki_project.document.infrastructure.extractor

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * 업로드 문서 바이트에서 임베딩용 순수 텍스트를 추출
 * - 추출 결과는 Redis Streams 이벤트의 content로 FastAPI에 전달되어 청킹/임베딩됨
 * - 텍스트가 없는 문서(이미지 스캔 PDF 등)나 손상된 파일은 업로드 단계에서 400으로 거절
 */
@Component
class DocumentTextExtractor {

    companion object {
        // Windows 메모장 등에서 저장된 한글 TXT 대비 (UTF-8 디코딩 실패 시 폴백)
        private val MS949: Charset = Charset.forName("MS949")
        private const val UTF8_BOM = '﻿'
    }

    fun extract(docType: String, bytes: ByteArray): String {
        val text = try {
            when (docType) {
                "TXT", "MD" -> decodeText(bytes)
                "PDF" -> extractPdf(bytes)
                "DOCX" -> extractDocx(bytes)
                else -> throw BusinessException(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE)
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            // 확장자만 PDF/DOCX이고 실제로는 손상되었거나 다른 형식인 파일
            throw BusinessException(ErrorCode.DOCUMENT_TEXT_EXTRACTION_FAILED)
        }

        val normalized = text.trimStart(UTF8_BOM).trim()
        if (normalized.isBlank()) {
            throw BusinessException(ErrorCode.DOCUMENT_TEXT_EXTRACTION_FAILED)
        }
        return normalized
    }

    private fun decodeText(bytes: ByteArray): String {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, MS949)
        }
    }

    private fun extractPdf(bytes: ByteArray): String {
        return Loader.loadPDF(bytes).use { document ->
            PDFTextStripper().getText(document)
        }
    }

    private fun extractDocx(bytes: ByteArray): String {
        return XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            XWPFWordExtractor(document).use { it.text }
        }
    }
}
