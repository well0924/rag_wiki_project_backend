package com.example.wiki_project.document.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.document.application.dto.DocumentUploadCommand
import com.example.wiki_project.document.application.dto.DocumentUploadedEvent
import com.example.wiki_project.document.domain.entity.DocumentPermissionJpaEntity
import com.example.wiki_project.document.domain.mapper.toEntity
import com.example.wiki_project.document.domain.mapper.toModel
import com.example.wiki_project.document.domain.model.DocumentModel
import com.example.wiki_project.document.domain.model.DocumentStatus
import com.example.wiki_project.document.domain.model.PermissionLevel
import com.example.wiki_project.document.infrastructure.DocumentPermissionRepository
import com.example.wiki_project.document.infrastructure.DocumentRepository
import org.springframework.data.redis.connection.stream.StringRecord
import org.springframework.data.redis.core.StringRedisTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val documentPermissionRepository: DocumentPermissionRepository,
    private val redisTemplate: StringRedisTemplate
) {

    companion object {
        private const val STREAM_KEY = "document:upload:stream"
    }

    /**
     * 문서 업로드
     **/
    @Transactional
    suspend fun uploadDocument(command: DocumentUploadCommand): Long = withContext(Dispatchers.IO) {

        // 파일 확장자(docType) 맑게 파싱 (없으면 "UNKNOWN")
        val docType = command.originalFilename?.substringAfterLast('.', "UNKNOWN")?.uppercase() ?: "UNKNOWN"

        // 가짜 S3 보류 가드: 임시 URL 먼저 구워내기 (나중에 S3 주입 시 교체할 지점)
        // 주의: 실제 ID 기반으로 URL을 만들려면 영속화가 먼저 필요할 수 있으므로, 임시 범용 경로 규칙으로 조합
        val dummyContentUrl = "https://wiki-project-bucket.s3.ap-northeast-2.amazonaws.com/documents/pending_${command.title}.$docType"

        // RDB 인프라 레이어 저장 및 식별자(ID) 추출
        val documentId = saveDocumentWithPermissions(command, docType, dummyContentUrl)

        // Redis Streams 'XADD' 비동기 큐잉 처리
        val event = DocumentUploadedEvent(
            documentId = documentId.toString(),
            s3Url = dummyContentUrl,
            title = command.title,
            uploaderId = command.uploaderId.toString()
        )

        val record = StringRecord.of(event.toMap()).withStreamKey(STREAM_KEY)
        redisTemplate.opsForStream<String, String>().add(record)

        documentId
    }


    @Transactional
    fun saveDocumentWithPermissions(
        command: DocumentUploadCommand,
        docType: String,
        contentUrl: String
    ): Long {

        // 정의한 명품 도메인 모델 생성 (기본 상태: UPLOADED)
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

        // 문서 권한 테이블 벌크 생성
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
     * 권한 기반 문서 단건 상세 조회
     */
    @Transactional(readOnly = true)
    fun getDocumentById(documentId: Long, userGroupIds: List<Long>, isAdmin: Boolean): DocumentModel {
        val entity = documentRepository.findById(documentId)
            .orElseThrow { BusinessException(ErrorCode.DOCUMENT_NOT_FOUND) }

        // Soft Delete 벤 가드
        if (entity.isDeleted) throw BusinessException(ErrorCode.DOCUMENT_NOT_FOUND)

        // [보안 가드] 어드민이 아니라면 유저의 소속 부서 그룹 권한 교집합 체크
        if (!isAdmin) {
            val hasPermission = documentPermissionRepository.hasReadPermission(documentId, userGroupIds)
            if (!hasPermission) {
                throw BusinessException(ErrorCode.UNAUTHORIZED_DOCUMENT_ACCESS) // "접근 권한이 없는 기밀 문서입니다."
            }
        }

        return entity.toModel()
    }

    /**
     * 권한 기반 접근 가능한 전체 문서 목록 조회
     */
    @Transactional(readOnly = true)
    fun getAccessibleDocuments(userGroupIds: List<Long>, isAdmin: Boolean): List<DocumentModel> {
        // 복합 조인 인프라 쿼리에 조건 처리를 완전히 위임
        val entities = documentRepository.findAccessibleDocuments(userGroupIds, isAdmin)
        return entities.map { it.toModel() }
    }

    /**
     * 문서 논리 삭제 (Soft Delete)
     */
    @Transactional
    fun deleteDocument(documentId: Long, requesterId: Long, isAdmin: Boolean) {
        val entity = documentRepository.findById(documentId)
            .orElseThrow { BusinessException(ErrorCode.DOCUMENT_NOT_FOUND) }

        if (entity.isDeleted) throw BusinessException(ErrorCode.DOCUMENT_NOT_FOUND)

        // 도메인 모델로 변환하여 소유자 및 권한 체킹 비즈니스 룰 수행
        val documentModel = entity.toModel()
        val deletedModel = documentModel.delete(requesterId, isAdmin)

        // 상태값 역변환 후 영속성 컨텍스트 플러시 반영
        documentRepository.save(deletedModel.toEntity())
    }
}