package com.example.wiki_project.document.application.dto

import jakarta.validation.constraints.NotBlank

data class DocumentUploadRequest(
    @field:NotBlank(message = "문서 제목은 필수입니다.")
    val title: String,
    // 업로더는 클라이언트 입력이 아닌 인증 정보(@AuthenticationPrincipal)의 회원 ID를 사용
    val targetGroupIds: List<Long> = emptyList()
)