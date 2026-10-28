package com.example.wiki_project.searchhistory.application

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.common.exception.ErrorCode
import com.example.wiki_project.searchhistory.domain.mapper.toEntity
import com.example.wiki_project.searchhistory.domain.mapper.toModel
import com.example.wiki_project.searchhistory.domain.model.SearchHistoryModel
import com.example.wiki_project.searchhistory.infrastructure.SearchHistoryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchHistoryService(
    private val searchHistoryRepository: SearchHistoryRepository
) {

    /**
     * 1. RAG 검색 기록 생성 (Save History)
     * AI 서버의 답변과 참조한 청크 ID 목록을 질문과 함께 저장
     */
    @Transactional
    fun recordSearchHistory(
        memberId: Long,
        query: String,
        answer: String?,
        referencedChunkIds: List<String>
    ): Long {
        val model = SearchHistoryModel(
            memberId = memberId,
            query = query,
            answer = answer,
            referencedChunkIds = referencedChunkIds
        )

        val savedEntity = searchHistoryRepository.save(model.toEntity())
        return savedEntity.id ?: throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR)
    }

    /**
     * 2. 내 검색 기록 조회 (검색 기록 사이드바)
     * 특정 유저가 과거에 질의했던 내용들을 최근 순(ID 역순)으로 정렬해서 리스트 반환
     */
    @Transactional(readOnly = true)
    fun getMySearchHistories(memberId: Long): List<SearchHistoryModel> {
        val entities = searchHistoryRepository.findByMemberIdOrderByIdDesc(memberId)
        return entities.map { it.toModel() }
    }

    /**
     * 답변 품질 피드백 반영 (추천 / 비추천)
     * 사용자가 답변에 👍 / 👎 를 누르면 저장 (관리자 품질 모니터링에 사용)
     */
    @Transactional
    fun evaluateSearchQuality(historyId: Long, memberId: Long, isPositive: Boolean) {
        val entity = searchHistoryRepository.findById(historyId)
            .orElseThrow { BusinessException(ErrorCode.RESOURCE_NOT_FOUND) }

        // 타인의 검색 기록에 피드백을 남겨 품질 지표를 오염시키는 것을 차단
        if (entity.memberId != memberId) {
            throw BusinessException(ErrorCode.FORBIDDEN_HISTORY_ACCESS)
        }

        // 도메인 모델에서 피드백 변경 규칙 적용
        val model = entity.toModel()
        val updatedModel = model.updateFeedback(isPositive)

        // 변경 내용 반영 (트랜잭션 커밋 시 flush)
        searchHistoryRepository.save(updatedModel.toEntity())
    }

    /**
     * [관리자 전용] 전체 검색 기록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    fun getAllSearchHistoriesForAdmin(isAdmin: Boolean, pageable: Pageable): Page<SearchHistoryModel> {
        // 관리자만 조회 가능
        if (!isAdmin) {
            throw BusinessException(ErrorCode.ADMIN_ONLY)
        }

        val entities = searchHistoryRepository.findAllByOrderByIdDesc(pageable)
        return entities.map { it.toModel() }
    }

    /**
     * [관리자 전용] 👎 피드백을 받은 답변 목록 조회
     * 관리자는 이 목록으로 부족한 문서를 보강하거나 검색 / 프롬프트 설정을 조정.
     */
    @Transactional(readOnly = true)
    fun getWorstSearchHistoriesForAdmin(isAdmin: Boolean, pageable: Pageable): Page<SearchHistoryModel> {
        if (!isAdmin) {
            throw BusinessException(ErrorCode.ADMIN_ONLY)
        }

        val entities = searchHistoryRepository.findByFeedbackFalseOrderByIdDesc(pageable)
        return entities.map { it.toModel() }
    }
}