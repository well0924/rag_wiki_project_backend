package com.example.wiki_project.document.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.document.application.dto.DocumentUploadCommand
import com.example.wiki_project.document.application.dto.DocumentUploadedEvent
import com.example.wiki_project.document.domain.mapper.toEntity
import com.example.wiki_project.document.domain.mapper.toModel
import com.example.wiki_project.document.domain.model.DocumentModel
import com.example.wiki_project.document.domain.model.DocumentStatus
import com.example.wiki_project.document.infrastructure.DocumentPermissionRepository
import com.example.wiki_project.document.infrastructure.DocumentRepository
import org.springframework.data.redis.connection.stream.StringRecord
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val documentPermissionRepository: DocumentPermissionRepository,
    private val documentCommandRepository: DocumentCommandRepository,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>
) {

    companion object {
        private const val STREAM_KEY = "document:upload:stream"
        private val ALLOWED_DOC_TYPES = setOf("TXT", "PDF", "DOCX", "MD")
    }

    /**
    * 문서 업로드 (다중)
    **/
    suspend fun uploadDocuments(commends:List<DocumentUploadCommand>):List<Long> = coroutineScope {
        commends.map { uploadDocument(it) }
    }

    /**
     * 문서 업로드
     **/
    suspend fun uploadDocument(command: DocumentUploadCommand): Long = withContext(Dispatchers.IO) {

        // 파일 확장자(docType) 맑게 파싱 (없으면 "UNKNOWN")
        val docType = command.originalFilename?.substringAfterLast('.', "UNKNOWN")?.uppercase() ?: "UNKNOWN"

        if (docType !in ALLOWED_DOC_TYPES) {
            throw BusinessException(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE)
        }
        // 가짜 S3 보류 가드: 임시 URL 먼저 구워내기 (나중에 S3 주입 시 교체할 지점)
        // 주의: 실제 ID 기반으로 URL을 만들려면 영속화가 먼저 필요할 수 있으므로, 임시 범용 경로 규칙으로 조합
        // 파일명 오염 방지를 위해 고유한 임시 키 조합 (UUID 활용 추천)
        val uniqueKey = java.util.UUID.randomUUID().toString()
        val dummyContentUrl = "https://amazonaws.com${uniqueKey}_${command.title}.$docType"

        // RDB DB 작업은 확실하게 Dispatchers.IO로 격리하여 블로킹 수행
        val documentId = documentCommandRepository.saveDocumentWithPermissions(command, docType, dummyContentUrl)

        val extractedContent = if (docType == "TXT" || docType == "MD") {
            String(command.fileBytes, StandardCharsets.UTF_8)
        } else {
            "PDF/DOCX 바이너리 메타데이터 컨텍스트 가드선" // PDF 등은 나중에 파이썬이 s3Url로 긁어가게 유도
        }

        // Redis Streams 'XADD' 비동기 큐잉 처리
        val event = DocumentUploadedEvent(
            documentId = documentId.toString(),
            content = extractedContent,
            s3Url = dummyContentUrl,
            title = command.title,
            uploaderId = command.uploaderId.toString()
        )

        val record = StringRecord.of(event.toMap()).withStreamKey(STREAM_KEY)

        // awaitSingle()을 사용하여 스레드를 차단하지 않고(Non-blocking) 비동기 대기
        reactiveRedisTemplate.opsForStream<String, String>()
            .add(record)
            .awaitSingle()

        documentId
    }

    @Transactional
    fun updateDocumentStatus(documentId: Long, targetStatus: DocumentStatus) {
        val entity = documentRepository.findById(documentId)
            .orElseThrow { BusinessException(ErrorCode.DOCUMENT_NOT_FOUND) }

        if (entity.isDeleted) throw BusinessException(ErrorCode.DOCUMENT_NOT_FOUND)

        // 영속 엔티티를 순수 도메인 모델로 치환하여 비즈니스 유효성 검증 전개
        var documentModel = entity.toModel()
        documentModel = when (targetStatus) {
            DocumentStatus.PROCESSING -> documentModel.startProcessing()
            DocumentStatus.COMPLETED -> documentModel.completeIndexing()
            DocumentStatus.FAILED -> documentModel.failIndexing()
            DocumentStatus.UPLOADED -> throw IllegalArgumentException("이미 업로드된 문서를 초기 UPLOADED 상태로 되돌릴 수 없습니다.")
        }

        // 도메인 규칙이 반영된 최종 상태를 엔티티에 격 격리 갱신
        entity.status = documentModel.status
        entity.updatedAt = LocalDateTime.now()
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

    /**
     * 문서 다운로드 요청
     */
    @Transactional(readOnly = true)
    fun downloadDocument(documentId: Long, userGroupIds: List<Long>, isAdmin: Boolean): DocumentModel {
        // 1. 기존 권한 가드 로직 재사용 (이미 잘 만들어져 있음)
        val document = getDocumentById(documentId, userGroupIds, isAdmin)

        // 2. 다운로드 처리를 위한 모델 반환
        // 나중에 여기서 s3Service.getPresignedUrl(document.s3Url)을 호출할 예정
        return document
    }
}