package com.example.wiki_project.searchhistory.infrastructure

import com.example.wiki_project.searchhistory.domain.entity.SearchHistoryEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SearchHistoryRepository: JpaRepository<SearchHistoryEntity,Long> {
    // 회원의 검색 기록 최근 순 조회
    fun findByMemberIdOrderByIdDesc(memberId: Long): List<SearchHistoryEntity>
    // 전체 검색 기록 페이지 조회 (관리자용)
    fun findAllByOrderByIdDesc(pageable: Pageable): Page<SearchHistoryEntity>

    // 비추천(false) 피드백을 받은 답변 조회
    // 어떤 질문에서 답변 품질이 낮은지(환각 등) 추적하는 용도
    fun findByFeedbackFalseOrderByIdDesc(pageable: Pageable): Page<SearchHistoryEntity>
}