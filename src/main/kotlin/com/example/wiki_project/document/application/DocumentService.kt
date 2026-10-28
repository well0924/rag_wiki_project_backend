package com.example.wiki_project.document.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.document.application.dto.DocumentDownload
import com.example.wiki_project.document.application.dto.DocumentUploadCommand
import com.example.wiki_project.document.application.dto.DocumentUploadedEvent
import com.example.wiki_project.document.domain.mapper.toEntity
import com.example.wiki_project.document.domain.mapper.toModel
import com.example.wiki_project.document.domain.model.DocumentModel
import com.example.wiki_project.document.domain.model.DocumentStatus
import com.example.wiki_project.document.infrastructure.DocumentPermissionRepository
import com.example.wiki_project.document.infrastructure.DocumentRepository
import com.example.wiki_project.document.infrastructure.extractor.DocumentTextExtractor
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
import org.springframework.data.redis.connection.stream.StringRecord
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val documentPermissionRepository: DocumentPermissionRepository,
    private val documentCommandRepository: DocumentCommandRepository,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val documentTextExtractor: DocumentTextExtractor,
    private val fileStorage: FileStorage
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val STREAM_KEY = "document:upload:stream"
        // 처리 완료(ACK)된 메시지도 스트림에는 계속 남으므로 최근 약 1,000건만 유지 (본문 포함이라 메모리 상한 확보)
        // - '~' 근사 트리밍: 정확히 자르지 않고 내부 노드 단위로 잘라 XADD 비용을 낮춤
        val STREAM_ADD_OPTIONS: XAddOptions = XAddOptions.maxlen(1000).approximateTrimming(true)
        private val ALLOWED_DOC_TYPES = setOf("TXT", "PDF", "DOCX", "MD")
    }

    /**
     * 확장자 검증 및 텍스트 추출까지 끝난 업로드 준비 데이터
     */
    private class PreparedUpload(
        val command: DocumentUploadCommand,
        val docType: String,
        val content: String
    )

    /**
    * 문서 업로드 (다중)
    * - 모든 파일의 형식 검증 / 텍스트 추출을 먼저 끝낸 뒤 저장하여,
    *   일부 파일 실패 시 앞선 파일만 DB에 남는 부분 저장을 방지
    **/
    suspend fun uploadDocuments(commends:List<DocumentUploadCommand>):List<Long> = withContext(Dispatchers.IO) {
        val prepared = commends.map { prepareUpload(it) }
        prepared.map { saveAndPublish(it) }
    }

    /**
     * 문서 업로드
     **/
    suspend fun uploadDocument(command: DocumentUploadCommand): Long = withContext(Dispatchers.IO) {
        saveAndPublish(prepareUpload(command))
    }

    /**
     * 확장자 화이트리스트 검증 + 임베딩용 텍스트 추출 (DB 저장 전 단계)
     */
    private fun prepareUpload(command: DocumentUploadCommand): PreparedUpload {
        // 파일 확장자(docType) 맑게 파싱 (없으면 "UNKNOWN")
        val docType = command.originalFilename?.substringAfterLast('.', "UNKNOWN")?.uppercase() ?: "UNKNOWN"

        if (docType !in ALLOWED_DOC_TYPES) {
            throw BusinessException(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE)
        }

        // 텍스트가 없는 문서는 저장 전에 거절 (UPLOADED 상태로 방치되는 문서 방지)
        val content = documentTextExtractor.extract(docType, command.fileBytes)
        return PreparedUpload(command, docType, content)
    }

    private suspend fun saveAndPublish(prepared: PreparedUpload): Long {
        val command = prepared.command
        val docType = prepared.docType

        // 원본 파일 저장 (key 는 사용자 입력 제목 대신 UUID 로 생성 → 파일명 충돌 / 경로 조작 방지)
        // - S3 도입 시 FileStorage 구현체만 교체하고, key 는 S3 object key 로 그대로 사용
        val storageKey = "documents/${java.util.UUID.randomUUID()}.${docType.lowercase()}"
        fileStorage.store(storageKey, command.fileBytes)

        // RDB DB 작업은 확실하게 Dispatchers.IO로 격리하여 블로킹 수행
        // - DB 저장 실패 시 방금 저장한 파일은 고아 파일이 되므로 정리 후 예외 전파
        val documentId = try {
            documentCommandRepository.saveDocumentWithPermissions(command, docType, storageKey)
        } catch (e: Exception) {
            fileStorage.delete(storageKey)
            throw e
        }

        // Redis Streams 'XADD' 비동기 큐잉 처리
        val event = DocumentUploadedEvent(
            documentId = documentId.toString(),
            content = prepared.content,
            s3Url = storageKey,
            title = command.title,
            uploaderId = command.uploaderId.toString()
        )

        val record = StringRecord.of(event.toMap()).withStreamKey(STREAM_KEY)

        // awaitSingle()을 사용하여 스레드를 차단하지 않고(Non-blocking) 비동기 대기
        // - DB 저장은 끝났는데 발행만 실패하면 문서가 UPLOADED로 방치되므로 FAILED로 전환 후 예외 응답
        try {
            reactiveRedisTemplate.opsForStream<String, String>()
                .add(record, STREAM_ADD_OPTIONS)
                .awaitSingle()
        } catch (e: Exception) {
            log.error("문서 인덱싱 이벤트 발행 실패: documentId={}", documentId, e)
            documentCommandRepository.markIndexingFailed(documentId)
            throw BusinessException(ErrorCode.DOCUMENT_INDEXING_REQUEST_FAILED)
        }

        return documentId
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
    fun downloadDocument(documentId: Long, userGroupIds: List<Long>, isAdmin: Boolean): DocumentDownload {
        // 1. 기존 권한 가드 로직 재사용 (이미 잘 만들어져 있음)
        val document = getDocumentById(documentId, userGroupIds, isAdmin)

        // 2. 저장소에서 원본 파일 조회 (S3 도입 전 더미 URL 로 저장된 기존 문서는 파일이 없으므로 404)
        val resource = fileStorage.load(document.contentUrl)
            ?: throw BusinessException(ErrorCode.DOCUMENT_FILE_NOT_FOUND)

        return DocumentDownload(
            fileName = "${document.title}.${document.docType.lowercase()}",
            resource = resource
        )
    }
}