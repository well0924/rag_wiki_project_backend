package com.example.wiki_project.document.application.dto

import jakarta.validation.constraints.NotBlank

data class DocumentUploadRequest(
    @field:NotBlank(message = "문서 제목은 필수입니다.")
    val title: String,
    val uploaderId: Long,
    val targetGroupIds: List<Long> = emptyList()
)