package com.example.wiki_project.searchhistory.ui

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import com.example.wiki_project.searchhistory.application.SearchUsecase
import com.example.wiki_project.searchhistory.application.dto.SearchRequest
import com.example.wiki_project.searchhistory.application.dto.SearchResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val searchUsecase: SearchUsecase
) {

    /**
     * 권한 기반 RAG 자연어 검색
     * - 인증 필터가 적재한 최신 groupIds / isAdmin을 그대로 FastAPI 메타데이터 필터링에 전달
     */
    @Operation(
        summary = "RAG 자연어 검색",
        description = "로그인한 사용자의 소속 그룹 권한 범위 내 문서만 참조하여 AI 답변을 생성하고 검색 기록을 남깁니다.",
        security = [SecurityRequirement(name = "BearerAuth")]
    )
    @PostMapping
    suspend fun search(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @Valid @RequestBody request: SearchRequest
    ): ResponseEntity<SearchResponse> {
        val memberId = userDetails.member.id
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)

        val response = searchUsecase.executeSearch(
            memberId = memberId,
            query = request.query.trim(),
            userGroupIds = userDetails.groupIds,
            isAdmin = userDetails.isAdmin
        )
        return ResponseEntity.ok(response)
    }
}
