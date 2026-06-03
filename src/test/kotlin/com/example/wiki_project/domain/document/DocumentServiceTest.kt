package com.example.wiki_project.domain.document

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.document.application.DocumentCommandRepository
import com.example.wiki_project.document.application.DocumentService
import com.example.wiki_project.document.application.dto.DocumentUploadCommand
import com.example.wiki_project.document.domain.entity.DocumentEntity
import com.example.wiki_project.document.domain.mapper.toEntity
import com.example.wiki_project.document.domain.model.DocumentModel
import com.example.wiki_project.document.domain.model.DocumentStatus
import com.example.wiki_project.document.infrastructure.DocumentPermissionRepository
import com.example.wiki_project.document.infrastructure.DocumentRepository
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StringRecord
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStreamOperations
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import reactor.core.publisher.Mono
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class DocumentServiceTest {

    @Mock private lateinit var documentRepository: DocumentRepository
    @Mock private lateinit var documentPermissionRepository: DocumentPermissionRepository
    @Mock private lateinit var redisTemplate: StringRedisTemplate
    @Mock private lateinit var reactiveRedisTemplate: ReactiveRedisTemplate<String, String>
    @Mock private lateinit var reactiveStreamOperations: ReactiveStreamOperations<String, String, String>
    @Mock private lateinit var documentCommandRepository: DocumentCommandRepository

    private lateinit var documentService: DocumentService

    @BeforeEach
    fun setUp() {
        documentService = DocumentService(
            documentRepository = documentRepository,
            documentPermissionRepository = documentPermissionRepository,
            reactiveRedisTemplate = reactiveRedisTemplate,
            documentCommandRepository = documentCommandRepository
        )
    }

    @Nested
    @DisplayName("문서 업로드(uploadDocument) 검증")
    inner class UploadDocumentValidation {
        @Test
        @DisplayName("정상적인 Command가 인입되면 메타데이터 저장, 권한 그룹 생성, Redis Streams 발행이 원자적으로 작동해야 한다")
        fun uploadDocumentSuccess() = runTest { // 코루틴 비동기 테스트용 스코프 가동
            // given
            val command = DocumentUploadCommand(
                title = "사내 가이드라인",
                originalFilename = "guide.pdf",
                fileSize = 1024L,
                fileBytes = "dummy data".toByteArray(),
                uploaderId = 1L,
                targetGroupIds = listOf(10L, 20L)
            )

            // Mocking 선언
            `when`(documentCommandRepository.saveDocumentWithPermissions(
                any(DocumentUploadCommand::class.java),
                anyString(),
                anyString()
            ))
                .thenReturn(99L)
            `when`(reactiveRedisTemplate.opsForStream<String, String>()).thenReturn(reactiveStreamOperations)
            `when`(reactiveStreamOperations.add(any(StringRecord::class.java))).thenReturn(Mono.just(RecordId.of("17800000-0")))

            // when
            val resultId = documentService.uploadDocument(command)

            // then
            assertThat(resultId).isEqualTo(99L)
            verify(documentCommandRepository).saveDocumentWithPermissions(any(DocumentUploadCommand::class.java), anyString(), anyString())
            verify(reactiveStreamOperations).add(any(StringRecord::class.java)) // Redis Streams XADD 호출 팩트 체크
        }

        @Test
        @DisplayName("지원하지 않는 문서 확장자(EXE 등)가 인입되면 인프라를 타기 전에 UNSUPPORTED_DOCUMENT_TYPE 예외가 터져야 한다")
        fun uploadDocumentFailInvalidExtension() = runTest {
            // given
            val command = DocumentUploadCommand(
                title = "해킹툴",
                originalFilename = "malware.exe",
                fileSize = 9999L,
                fileBytes = "malware data".toByteArray(), // 빈 배열(0) 대신 더미 바이트 데이터 주입해서 생성자 가드 통과
                uploaderId = 1L,
                targetGroupIds = emptyList()
            )

            // when & then
            var thrownException: BusinessException? = null
            try {
                documentService.uploadDocument(command)
            } catch (e: BusinessException) {
                thrownException = e
            }

            assertThat(thrownException).isNotNull
            assertThat(thrownException!!.errorCode).isEqualTo(ErrorCode.UNSUPPORTED_DOCUMENT_TYPE)

            // 인프라성 자원 호출이 차단되었는지 완벽 검증
            verifyNoInteractions(documentCommandRepository, reactiveRedisTemplate)
        }
    }

    @Nested
    @DisplayName("AI 인덱싱 상태 변경(updateDocumentStatus) 검증")
    inner class UpdateDocumentStatusValidation {
        @Test
        @DisplayName("COMPLETED 상태 변경 요청 시 도메인 규칙에 따라 상태가 전이되고 더티체킹 반영되어야 한다")
        fun updateStatusSuccess() {
            // given
            val docId = 1L
            val documentEntity = DocumentEntity(
                id = docId, title = "위키 가이드", contentUrl = "url",
                docType = "MD", fileSize = 100L, createdBy = 1L,
                status = DocumentStatus.PROCESSING, isDeleted = false
            )

            `when`(documentRepository.findById(docId)).thenReturn(Optional.of(documentEntity))

            // when
            documentService.updateDocumentStatus(docId, DocumentStatus.COMPLETED)

            // then
            assertThat(documentEntity.status).isEqualTo(DocumentStatus.COMPLETED)
        }

        @Test
        @DisplayName("이미 인덱싱이 완료된 문서를 다시 PROCESSING으로 강제 역전이 하려 하면 예외가 터진다")
        fun updateStatusFailInvalidTransition() {
            // given
            val docId = 1L
            val documentEntity = DocumentEntity(
                id = docId, title = "이미 완료된 문서", contentUrl = "url",
                docType = "PDF", fileSize = 100L, createdBy = 1L,
                status = DocumentStatus.COMPLETED, isDeleted = false
            )

            `when`(documentRepository.findById(docId)).thenReturn(Optional.of(documentEntity))

            // when & then
            assertThatThrownBy { documentService.updateDocumentStatus(docId, DocumentStatus.PROCESSING) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("이미 인덱싱이 완료된 문서입니다.")
        }
    }

    @Nested
    @DisplayName("권한 기반 문서 단건 상세 조회 검증")
    inner class GetDocumentByIdValidation {

        @Test
        @DisplayName("일반 회원이 권한이 없는 부서의 기밀 문서를 상세 조회하려고 하면 비즈니스 예외를 뱉어야 한다")
        fun getDocumentFailWhenUnauthorized() {
            // given
            val docId = 100L
            val myGroupIds = listOf(1L, 2L)
            val documentEntity = DocumentEntity(
                id = docId, title = "핵심 아키텍처 기밀", contentUrl = "s3://...",
                docType = "PDF", fileSize = 2048L, createdBy = 999L, isDeleted = false
            )

            `when`(documentRepository.findById(docId)).thenReturn(Optional.of(documentEntity))
            // 내가 속한 groupIds 중 이 문서의 접근 권한 목록과 매칭되는 교집합이 없다고 정의 (false)
            `when`(documentPermissionRepository.hasReadPermission(docId, myGroupIds)).thenReturn(false)

            // when & then
            val exception = org.junit.jupiter.api.assertThrows<BusinessException> {
                documentService.getDocumentById(docId, myGroupIds, isAdmin = false)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.UNAUTHORIZED_DOCUMENT_ACCESS)
        }

        @Test
        @DisplayName("어드민(isAdmin=true) 유저가 찌르면, 권한 매핑 테이블을 스캔하는 I/O 비용 없이 프리패스로 통과해야 한다")
        fun getDocumentSuccessWhenAdminUser() {
            // given
            val docId = 200L
            val documentEntity = DocumentEntity(
                id = docId, title = "전사 공통 문서", contentUrl = "s3://...",
                docType = "MD", fileSize = 512L, createdBy = 888L, isDeleted = false
            )

            `when`(documentRepository.findById(docId)).thenReturn(Optional.of(documentEntity))

            // when
            val result = documentService.getDocumentById(docId, userGroupIds = emptyList(), isAdmin = true)

            // then
            assertThat(result.title).isEqualTo("전사 공통 문서")
            // 검증: 어드민이므로 권한 레포지토리의 existsBy...는 단 한 번도 호출되지 않았어야 함 (성능 최적화 증명)
            verify(documentPermissionRepository, times(0)).hasReadPermission(anyLong(), anyList())
        }

        @Test
        @DisplayName("이미 삭제된 문서를 조회하면 DOCUMENT_NOT_FOUND 예외를 던진다")
        fun getDocumentFailWhenDeleted() {
            val docId = 100L
            val deletedEntity = DocumentEntity(
                id = docId, title = "삭제된 문서", contentUrl = "s3://",
                docType = "PDF", fileSize = 100L, createdBy = 1L, isDeleted = true
            )

            `when`(documentRepository.findById(docId)).thenReturn(Optional.of(deletedEntity))

            val exception = org.junit.jupiter.api.assertThrows<BusinessException> {
                documentService.getDocumentById(docId, emptyList(), isAdmin = false)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("문서 Soft Delete 및 권한 가드 검증")
    inner class DeleteDocumentValidation {

        @Test
        @DisplayName("문서 삭제 시 요청자가 문서의 소유자(createdBy)가 아니고 어드민도 아니라면 삭제를 거부한다")
        fun deleteFailWhenNotOwnerAndNotAdmin() {
            // given
            val docId = 300L
            val ownerId = 777L
            val hackerId = 444L // 타인
            val documentEntity = DocumentEntity(
                id = docId, title = "주인 있는 문서", contentUrl = "s3://",
                docType = "TXT", fileSize = 10L, createdBy = ownerId, isDeleted = false
            )

            `when`(documentRepository.findById(docId)).thenReturn(Optional.of(documentEntity))

            // when & then
            assertThatThrownBy { documentService.deleteDocument(docId, requesterId = hackerId, isAdmin = false) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("해당 문서를 삭제할 권한이 없습니다.")
        }

        @Test
        @DisplayName("문서 소유자가 삭제 요청하면 정상 처리된다")
        fun deleteSuccessWhenOwner() {
            val docId = 300L
            val ownerId = 777L
            val documentEntity = DocumentEntity(
                id = docId, title = "내 문서", contentUrl = "s3://",
                docType = "TXT", fileSize = 10L, createdBy = ownerId, isDeleted = false
            )

            `when`(documentRepository.findById(docId)).thenReturn(Optional.of(documentEntity))
            `when`(documentRepository.save(any())).thenAnswer { it.arguments[0] }

            documentService.deleteDocument(docId, requesterId = ownerId, isAdmin = false)

            verify(documentRepository).save(any())
        }

        @Test
        @DisplayName("이미 완료된 문서를 다시 Processing으로 돌리려 하면 예외가 발생한다")
        fun startProcessingFailWhenCompleted() {
            val model = DocumentModel(
                id = 1L, title = "완료된 문서", contentUrl = "url",
                docType = "PDF", fileSize = 100L, createdBy = 1L,
                status = DocumentStatus.COMPLETED
            )

            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                model.startProcessing()
            }
        }

    }

    // Mockito 인자 매칭용 유틸 함수 모킹
    private fun <T> any(type: Class<T>): T = org.mockito.Mockito.any(type)
}