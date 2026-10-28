package com.example.wiki_project.document.application.dto

data class DocumentUploadedEvent(val documentId: String,
                                 val content: String,
                                 val s3Url: String,
                                 val title: String,
                                 val uploaderId: String) {
    /**
     * Redis Streams 메시지 필드(Map)로 변환
     */
    fun toMap(): Map<String, String> {
        return mapOf(
            "documentId" to this.documentId,
            "content" to content,
            "s3Url" to this.s3Url,
            "title" to this.title,
            "uploaderId" to this.uploaderId
        )
    }
}
