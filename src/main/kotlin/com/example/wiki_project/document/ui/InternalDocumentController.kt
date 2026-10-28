package com.example.wiki_project.document.ui

import com.example.wiki_project.document.application.DocumentService
import com.example.wiki_project.document.domain.model.DocumentStatus
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * AI 서버(FastAPI) 전용 내부 API
 * - SecurityConfig에서 /api/v1/internal/ 하위 경로는 X-Internal-Api-Key 인증(ROLE_INTERNAL)만 허용
 */
@RestController
@RequestMapping("/api/v1/internal/documents")
class InternalDocumentController(
    private val documentService: DocumentService
) {

    /**
     * AI 인덱싱 상태 전이 갱신 API
     * - AI 서버 워커가 인덱싱 시작 / 완료 / 실패 시점에 호출하는 콜백 엔드포인트입니다.
     */
    @Operation(summary = "AI 인덱싱 상태 변경 (파이썬 역호출용)", description = "파이썬 컨슈머가 파싱 척도에 따라 PROCESSING, COMPLETED, FAILED 상태로 전이시킵니다. X-Internal-Api-Key 헤더가 필요합니다.")
    @PatchMapping("/{documentId}/status")
    fun updateDocumentStatus(
        @PathVariable documentId: Long,
        @RequestParam("status") targetStatus: DocumentStatus
    ): ResponseEntity<Unit> {
        documentService.updateDocumentStatus(documentId, targetStatus)
        return ResponseEntity.ok().build()
    }
}
