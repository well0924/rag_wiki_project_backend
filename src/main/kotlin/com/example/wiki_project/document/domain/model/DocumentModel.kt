package com.example.wiki_project.document.domain.model

import java.time.LocalDateTime

data class DocumentModel(
    val id: Long? = null,
    val title: String,
    val contentUrl: String,
    val docType: String,
    val fileSize: Long,
    val status: DocumentStatus = DocumentStatus.UPLOADED,
    val isDeleted: Boolean = false,
    val createdBy: Long,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    // 완료된 문서는 다시 처리 중 상태로 되돌릴 수 없음 (FAILED → PROCESSING 재시도는 허용)
    fun startProcessing(): DocumentModel {
        check(this.status != DocumentStatus.COMPLETED) { "이미 인덱싱이 완료된 문서입니다." }
        return copy(status = DocumentStatus.PROCESSING, updatedAt = LocalDateTime.now())
    }

    fun completeIndexing(): DocumentModel {
        return copy(status = DocumentStatus.COMPLETED, updatedAt = LocalDateTime.now())
    }

    fun failIndexing(): DocumentModel {
        return copy(status = DocumentStatus.FAILED, updatedAt = LocalDateTime.now())
    }

    // 문서 삭제 권한 검증 (작성자 또는 관리자만 가능)
    fun delete(deleterId: Long, isAdmin: Boolean): DocumentModel {
        check(this.createdBy == deleterId || isAdmin) { "해당 문서를 삭제할 권한이 없습니다." }
        return copy(isDeleted = true, updatedAt = LocalDateTime.now())
    }
}