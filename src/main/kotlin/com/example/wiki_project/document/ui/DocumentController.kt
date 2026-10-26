package com.example.wiki_project.document.ui

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.document.application.DocumentService
import com.example.wiki_project.document.application.dto.DocumentUploadCommand
import com.example.wiki_project.document.application.dto.DocumentUploadRequest
import com.example.wiki_project.document.domain.model.DocumentModel
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController(
    private val documentService: DocumentService
) {

    /**
     * 위키 문서 파일 업로드 및 비동기 큐 적재
     * - 멀티파트 폼 데이터 양식으로 파일과 텍스트 데이터를 한방에 바인딩합니다.
     * - @ModelAttribute를 통해 쿼리 파라미터/폼 데이터를 DocumentUploadRequest 객체로 매핑합니다.
     */
    @Operation(summary = "위키 문서 업로드", description = "파일 화이트리스트 검증을 통과한 문서를 RDB에 영속화하고 Redis Streams에 비동기 이벤트를 발행합니다.")
    @PostMapping("/upload", consumes = ["multipart/form-data"])
    suspend fun uploadDocument(
        @AuthenticationPrincipal customUserDetails: CustomUserDetails,
        @Valid @ModelAttribute request: DocumentUploadRequest, // 제목, 업로더 아이디, 그룹 가리개 한방에 수신
        @RequestPart("file") files: List<MultipartFile>
    ): ResponseEntity<Map<String, Any>> {

        val uploaderId = customUserDetails.member.id
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)

        // 💡 2. [보안 방어선] 권한 가드
        // 만약 일반 유저가 요청한 targetGroupIds가 자기가 소속된 그룹들(userDetails.groupIds)에 포함되지 않는다면 차단
        if (!customUserDetails.isAdmin) { // 어드민이 아니라면 권한 체크 작동
            val hasPermission = customUserDetails.groupIds.containsAll(request.targetGroupIds)
            if (!hasPermission) {
                throw BusinessException(ErrorCode.FORBIDDEN_DOCUMENT_ACCESS) // 403 에러코드 권한 차단
            }
        }
        require(files.isNotEmpty()) { "업로드할 파일이 비어있습니다." }

        // [매핑 레이어] DTO와 멀티파트 정보의 융합을 통한 무결한 Command 객체 생성
        val command = files.map { file ->
            DocumentUploadCommand(
                title = request.title,
                originalFilename = file.originalFilename,
                fileSize = file.size,
                fileBytes = file.bytes,
                uploaderId = uploaderId,
                targetGroupIds = request.targetGroupIds
            )
        }

        val documentId = documentService.uploadDocuments(command)

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "documentId" to documentId,
                "message" to "문서 권한 매핑 및 비동기 큐 적재 성공"
            )
        )
    }

    /**
     * 권한 기반 접근 가능한 위키 문서 목록 전체 조회
     */
    @Operation(summary = "접근 가능 문서 목록 조회", description = "현재 로그인한 임직원의 부서 권한 및 관리자 권한 여부에 매핑되는 전체 문서 목록을 긁어옵니다.")
    @GetMapping
    fun getAccessibleDocuments(
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<List<DocumentModel>> {
        val documents = documentService.getAccessibleDocuments(
            userGroupIds = userDetails.groupIds,
            isAdmin = userDetails.isAdmin
        )
        return ResponseEntity.ok(documents)
    }

    /**
     * 권한 기반 위키 문서 단건 상세 조회
     */
    @Operation(summary = "문서 상세 단건 조회", description = "특정 문서의 메타데이터를 조회합니다. 권한 교집합이 없는 경우 403 보안 가드가 작동합니다.")
    @GetMapping("/{documentId}")
    fun getDocumentById(
        @PathVariable documentId: Long,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<DocumentModel> {
        val document = documentService.getDocumentById(
            documentId = documentId,
            userGroupIds = userDetails.groupIds,
            isAdmin = userDetails.isAdmin
        )
        return ResponseEntity.ok(document)
    }

    /**
     * 문서 논리 삭제 (Soft Delete)
     */
    @Operation(summary = "문서 소유자 기반 논리 삭제", description = "문서를 소프트 딜리트 처리합니다. 작성자 본인이거나 시스템 어드민만 수행 가능합니다.")
    @DeleteMapping("/{documentId}")
    fun deleteDocument(
        @PathVariable documentId: Long,
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<Unit> {
        documentService.deleteDocument(
            documentId = documentId,
            requesterId = userDetails.member.id ?: throw IllegalArgumentException("유저 식별 정보가 누락되었습니다."),
            isAdmin = userDetails.isAdmin
        )
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{documentId}/download")
    suspend fun downloadDocument(
        @PathVariable documentId: Long,
        @AuthenticationPrincipal user: CustomUserDetails
    ): ResponseEntity<Resource> {

        // 서비스 호출
        val document = documentService.downloadDocument(
            documentId,
            user.groupIds,
            user.isAdmin
        )

        // 파일 로드 (현재는 로컬 경로라 가정, 나중에 S3로 바꾸면 됨)
        val file = File(document.contentUrl) // 또는 s3Url을 통해 다운로드
        val resource = FileSystemResource(file)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${document.title}\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource)
    }

}