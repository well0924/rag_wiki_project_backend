package com.example.wiki_project.document.domain.model

import java.time.LocalDateTime

class DocumentModel(
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
    // 비즈니스 규칙: 이미 완료된 문서를 다시 프로세싱 상태로 돌릴 수 없다 등
    fun startProcessing(): DocumentModel {
        check(this.status != DocumentStatus.COMPLETED) { "이미 인덱싱이 완료된 문서입니다." }
        return copyWith(DocumentStatus.PROCESSING)
    }

    fun completeIndexing(): DocumentModel {
        return copyWith(DocumentStatus.COMPLETED)
    }

    fun failIndexing(): DocumentModel {
        return copyWith(DocumentStatus.FAILED)
    }

    // 문서 삭제 권한 검증 규칙 (소유자 혹은 어드민만 가능)
    fun delete(deleterId: Long, isAdmin: Boolean): DocumentModel {
        check(this.createdBy == deleterId || isAdmin) { "해당 문서를 삭제할 권한이 없습니다." }
        return copyWith(isDeleted = true)
    }

    private fun copyWith(
        status: DocumentStatus = this.status,
        isDeleted: Boolean = this.isDeleted
    ): DocumentModel {
        return DocumentModel(
            id = this.id,
            title = this.title,
            contentUrl = this.contentUrl,
            docType = this.docType,
            fileSize = this.fileSize,
            status = status,
            createdBy = this.createdBy,
            isDeleted = isDeleted, // 복사 시 상태값 전달
            createdAt = this.createdAt,
            updatedAt = LocalDateTime.now()
        )
    }
}