package com.example.wiki_project.searchhistory.ui

import com.example.wiki_project.member.infrastructure.security.auth.CustomUserDetails
import com.example.wiki_project.searchhistory.application.SearchHistoryService
import com.example.wiki_project.searchhistory.application.dto.FeedbackRequest
import com.example.wiki_project.searchhistory.domain.model.SearchHistoryModel
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/search-histories")
class SearchHistoryController(
    private val searchHistoryService: SearchHistoryService
) {

    @Operation(summary = "내 검색 기록 조회", security = [SecurityRequirement(name = "BearerAuth")])
    @GetMapping("/me")
    fun getMyHistories(
        @AuthenticationPrincipal userDetails: CustomUserDetails
    ): ResponseEntity<List<SearchHistoryModel>> {
        val result = searchHistoryService.getMySearchHistories(userDetails.member.id!!)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "검색 결과 피드백 평가", security = [SecurityRequirement(name = "BearerAuth")])
    @PatchMapping("/{historyId}/feedback")
    fun evaluateFeedback(
        @PathVariable historyId: Long,
        @RequestBody request: FeedbackRequest
    ): ResponseEntity<Unit> {
        searchHistoryService.evaluateSearchQuality(historyId, request.isPositive)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "[어드민] 전체 검색 기록 조회", security = [SecurityRequirement(name = "BearerAuth")])
    @GetMapping("/admin")
    fun getAllHistoriesForAdmin(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        pageable: Pageable
    ): ResponseEntity<Page<SearchHistoryModel>> {
        val result = searchHistoryService.getAllSearchHistoriesForAdmin(userDetails.isAdmin, pageable)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "[어드민] Worst 검색 기록 조회", security = [SecurityRequirement(name = "BearerAuth")])
    @GetMapping("/admin/worst")
    fun getWorstHistoriesForAdmin(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        pageable: Pageable
    ): ResponseEntity<Page<SearchHistoryModel>> {
        val result = searchHistoryService.getWorstSearchHistoriesForAdmin(userDetails.isAdmin, pageable)
        return ResponseEntity.ok(result)
    }
}