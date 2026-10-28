package com.example.wiki_project.document.application.dto

data class DocumentUploadCommand(
    val title: String,
    val originalFilename: String?,
    val fileSize: Long,
    val fileBytes: ByteArray,
    val uploaderId: Long,
    val targetGroupIds: List<Long>
) {
    init {
        // 컨트롤러 검증과 별개로 Command 생성 시점에 한 번 더 검증
        require(title.isNotBlank()) { "문서 제목은 공백일 수 없습니다." }
        require(fileBytes.isNotEmpty()) { "업로드할 로우 바이트 파일 데이터가 비어있습니다." }
    }
}
