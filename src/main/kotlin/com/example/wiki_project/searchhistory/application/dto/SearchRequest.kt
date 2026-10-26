package com.example.wiki_project.searchhistory.application.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SearchRequest(
    @field:NotBlank(message = "질문 내용은 공백일 수 없습니다.")
    @field:Size(max = 1000, message = "질문은 1000자를 초과할 수 없습니다.")
    val query: String
)
