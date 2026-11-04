package com.example.wiki_project.document.application.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class DocumentUploadRequest(
    @field:NotBlank(message = "문서 제목은 필수입니다.")
    val title: String,
    // 업로더는 클라이언트 입력이 아닌 인증 정보(@AuthenticationPrincipal)의 회원 ID를 사용
    // 공개 그룹이 없으면 아무도 볼 수 없는 문서가 되므로 한 개 이상 필수
    @field:NotEmpty(message = "문서를 공개할 그룹을 하나 이상 선택해 주세요.")
    val targetGroupIds: List<Long> = emptyList()
)
