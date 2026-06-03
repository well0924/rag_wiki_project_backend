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

@Component
class DocumentCommandRepository(
    private val documentRepository: DocumentRepository,
    private val documentPermissionRepository: DocumentPermissionRepository
) {

    /**
     * 다른 클래스에서 호출하므로 @Transactional 프록시가 완벽하게 작동.
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
}