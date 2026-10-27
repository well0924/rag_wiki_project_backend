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
     * Python FastAPI 챗봇 엔진으로부터 전달받은 질의응답 및 참조 벡터 노드 ID 세트를 RDB에 아카이빙
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
        return savedEntity.id ?: throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR) // 적절한 예외처리 매핑
    }

    /**
     * 2. 내 대화방 목록 조회 (마이페이지 / 챗봇 히스토리 사이드바)
     * 특정 유저가 과거에 질의했던 내용들을 최근 순(ID 역순)으로 정렬해서 리스트 반환
     */
    @Transactional(readOnly = true)
    fun getMySearchHistories(memberId: Long): List<SearchHistoryModel> {
        val entities = searchHistoryRepository.findByMemberIdOrderByIdDesc(memberId)
        return entities.map { it.toModel() }
    }

    /**
     * 품질 피드백 반영 (추천 / 비추천 토글 가드)
     * 유저가 AI의 답변을 보고 따봉(추천)을 누르거나 비추천을 누를 때 품질 메트릭을 동기화
     */
    @Transactional
    fun evaluateSearchQuality(historyId: Long, memberId: Long, isPositive: Boolean) {
        val entity = searchHistoryRepository.findById(historyId)
            .orElseThrow { BusinessException(ErrorCode.RESOURCE_NOT_FOUND) } // 적절한 히스토리 없음 에러코드 매핑

        // 타인의 검색 기록에 피드백을 남겨 품질 지표를 오염시키는 것을 차단
        if (entity.memberId != memberId) {
            throw BusinessException(ErrorCode.FORBIDDEN_HISTORY_ACCESS)
        }

        // 도메인 모델로 격리 전환 후 피드백 업데이트 비즈니스 규칙 수행
        val model = entity.toModel()
        val updatedModel = model.updateFeedback(isPositive)

        // 영속성 컨텍스트 쓰기 지연 반영
        searchHistoryRepository.save(updatedModel.toEntity())
    }

    /**
     * [어드민 전용] 전사 임직원 검색 기록 모니터링 (Paging 적용)
     */
    @Transactional(readOnly = true)
    fun getAllSearchHistoriesForAdmin(isAdmin: Boolean, pageable: Pageable): Page<SearchHistoryModel> {
        // 보안 가드: 어드민이 아니면 원천 차단
        if (!isAdmin) {
            throw BusinessException(ErrorCode.ADMIN_ONLY)
        }

        val entities = searchHistoryRepository.findAllByOrderByIdDesc(pageable)
        return entities.map { it.toModel() }
    }

    /**
     * [어드민 전용] AI가 헛소리한 Worst 답변 목록 추출
     * 어드민은 이 목록을 보고 Python RAG 진영의 프롬프트를 수정하거나 문서 임베딩을 다시 구워야 함.
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