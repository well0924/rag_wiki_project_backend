package com.example.wiki_project.document.application.dto

data class DocumentUploadedEvent(val documentId: String,
                                 val s3Url: String,
                                 val title: String,
                                 val uploaderId: String) {
    /**
     * Redis 인메모리 스트림 적재를 위한 직렬화 맵 변환 매핑
     */
    fun toMap(): Map<String, String> {
        return mapOf(
            "documentId" to this.documentId,
            "s3Url" to this.s3Url,
            "title" to this.title,
            "uploaderId" to this.uploaderId
        )
    }
}
