package com.example.wiki_project.document.ui

import com.example.wiki_project.document.application.DocumentService
import com.example.wiki_project.document.application.dto.DocumentUploadCommand
import com.example.wiki_project.document.application.dto.DocumentUploadRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController(
    private val documentService: DocumentService
) {

    @PostMapping("/upload", consumes = ["multipart/form-data"])
    suspend fun uploadDocument(
        @Valid @ModelAttribute request: DocumentUploadRequest, // 제목, 업로더 아이디, 그룹 가리개 한방에 수신
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<Map<String, Any>> {

        require(!file.isEmpty) { "업로드할 파일이 비어있습니다." }

        // [매핑 레이어] DTO와 멀티파트 정보의 융합을 통한 무결한 Command 객체 생성
        val command = DocumentUploadCommand(
            title = request.title,
            originalFilename = file.originalFilename,
            fileSize = file.size,
            fileBytes = file.bytes,
            uploaderId = request.uploaderId,
            targetGroupIds = request.targetGroupIds
        )

        val documentId = documentService.uploadDocument(command)

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "documentId" to documentId,
                "message" to "문서 권한 매핑 및 비동기 큐 적재 성공"
            )
        )
    }
}