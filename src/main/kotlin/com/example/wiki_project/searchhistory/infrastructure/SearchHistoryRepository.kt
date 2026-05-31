package com.example.wiki_project.searchhistory.infrastructure

import com.example.wiki_project.searchhistory.domain.entity.SearchHistoryEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SearchHistoryRepository: JpaRepository<SearchHistoryEntity,Long> {
    // 유저 마이페이지 챗봇 대화방 리스트 역순 출력용 쿼리 메서드
    fun findByMemberIdOrderByIdDesc(memberId: Long): List<SearchHistoryEntity>
    // 전사 임직원의 모든 대화 기록을 페이지네이션으로 긁어오기
    fun findAllByOrderByIdDesc(pageable: Pageable): Page<SearchHistoryEntity>

    // 유저들이 "비추천(false)"을 박은 워스트(Worst) 답변만 모아서 모니터링
    // AI 모델이 어떤 질문에서 헛소리(Hallucination)를 하는지 추적하는 쿼리
    fun findByFeedbackFalseOrderByIdDesc(pageable: Pageable): Page<SearchHistoryEntity>
}