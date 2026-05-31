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
        // 서비스 진입 전 2차 최종 방어선 가드
        require(title.isNotBlank()) { "문서 제목은 공백일 수 없습니다." }
        require(fileBytes.isNotEmpty()) { "업로드할 로우 바이트 파일 데이터가 비어있습니다." }
    }
}
