package com.example.wiki_project.document.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.document.application.dto.DocumentUploadCommand
import com.example.wiki_project.document.domain.entity.DocumentPermissionJpaEntity
import com.example.wiki_project.document.domain.mapper.toEntity
import com.example.wiki_project.document.domain.model.DocumentModel
import com.example.wiki_project.document.domain.model.DocumentStatus
import com.example.wiki_project.document.domain.model.PermissionLevel
import com.example.wiki_project.document.infrastructure.DocumentPermissionRepository
import com.example.wiki_project.document.infrastructure.DocumentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class DocumentCommandRepository(
    private val documentRepository: DocumentRepository,
    private val documentPermissionRepository: DocumentPermissionRepository
) {

    /**
     * 별도 빈으로 분리해 외부에서 호출하므로 @Transactional 프록시가 적용됨 (같은 클래스 내부 호출 시 미적용 문제 회피).
     * 문서와 권한 테이블 생성이 하나의 트랜잭션으로 묶여 둘 중 하나라도 깨지면 전체 롤백.
     */
    @Transactional
    fun saveDocumentWithPermissions(command: DocumentUploadCommand, docType: String, contentUrl: String): Long {
        val documentEntity = DocumentModel(
            title = command.title,
            contentUrl = contentUrl,
            docType = docType,
            fileSize = command.fileSize,
            status = DocumentStatus.UPLOADED,
            createdBy = command.uploaderId
        ).toEntity()

        val savedDocument = documentRepository.save(documentEntity)
        val documentId = savedDocument.id ?: throw BusinessException(ErrorCode.DOCUMENT_SAVE_FAILED)

        if (command.targetGroupIds.isNotEmpty()) {
            val permissions = command.targetGroupIds.map { groupId ->
                DocumentPermissionJpaEntity(
                    documentId = documentId,
                    groupId = groupId,
                    permissionLevel = PermissionLevel.READ
                )
            }
            documentPermissionRepository.saveAll(permissions)
        }

        return documentId
    }

    /**
     * 인덱싱 큐 발행(XADD) 실패 시 UPLOADED 상태로 영원히 방치되지 않도록 즉시 실패 처리
     */
    @Transactional
    fun markIndexingFailed(documentId: Long) {
        val entity = documentRepository.findById(documentId).orElse(null) ?: return
        entity.status = DocumentStatus.FAILED
        entity.updatedAt = LocalDateTime.now()
    }
}