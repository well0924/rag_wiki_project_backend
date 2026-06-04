package com.example.wiki_project.domain.searchhistory

import com.example.wiki_project.common.exception.BusinessException
import com.example.wiki_project.searchhistory.application.SearchHistoryService
import com.example.wiki_project.searchhistory.domain.entity.SearchHistoryEntity
import com.example.wiki_project.searchhistory.infrastructure.SearchHistoryRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SearchHistoryServiceTest {

    @Mock private lateinit var searchHistoryRepository: SearchHistoryRepository

    @InjectMocks
    private lateinit var searchHistoryService: SearchHistoryService

    @Test
    @DisplayName("AI 피드백 평가 시 긍정(True) 신호를 주면 도메인 규칙을 타고 정상적으로 업데이트되어야 한다")
    fun updateFeedbackSuccess() {
        // given
        val historyId = 1L
        val existingEntity = SearchHistoryEntity(
            id = historyId,
            memberId = 10L,
            query = "JPA 복합키 쓰는법",
            answer = "IdClass를 쓰시면 됩니다.",
            referencedChunkIds = listOf("chunk-uuid-1", "chunk-uuid-2"),
            feedback = null // 초기값 미선택
        )

        `when`(searchHistoryRepository.findById(historyId)).thenReturn(Optional.of(existingEntity))
        `when`(searchHistoryRepository.save(any())).thenAnswer { it.arguments[0] }

        // when
        searchHistoryService.evaluateSearchQuality(historyId, isPositive = true)

        // then
        // 캡처 검증: save단에 최종 전달된 엔티티의 feedback이 true로 가공되었는지 팩트 확인
        verify(searchHistoryRepository).save(org.mockito.kotlin.check { savedEntity ->
            Assertions.assertThat(savedEntity.feedback).isTrue
        })
    }

    @Test
    @DisplayName("일반 회원이 어드민 전용 전사 검색 기록 조회를 요청하면 예외를 던지며 차단해야 한다")
    fun getAllHistoryFailWhenNotAdmin() {
        // given
        val isNotAdmin = false
        val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

        // when & then
        assertThrows<BusinessException> {
            searchHistoryService.getAllSearchHistoriesForAdmin(isAdmin = isNotAdmin, pageable = pageable)
        }

        // 💡 any() 대신 위에서 선언한 Non-Null 객체인 'pageable'을 그대로 박아준다!
        verify(searchHistoryRepository, times(0)).findAllByOrderByIdDesc(pageable)
    }

    @Test
    @DisplayName("존재하지 않는 히스토리 ID로 피드백 평가 시 예외를 던진다")
    fun updateFeedbackFailWhenNotFound() {
        `when`(searchHistoryRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<BusinessException> {
            searchHistoryService.evaluateSearchQuality(999L, isPositive = true)
        }
    }

    @Test
    @DisplayName("일반 회원이 Worst 검색 기록 조회를 요청하면 예외를 던진다")
    fun getWorstHistoryFailWhenNotAdmin() {
        val pageable = org.springframework.data.domain.PageRequest.of(0, 10)

        assertThrows<BusinessException> {
            searchHistoryService.getWorstSearchHistoriesForAdmin(isAdmin = false, pageable = pageable)
        }

        verify(searchHistoryRepository, times(0)).findByFeedbackFalseOrderByIdDesc(pageable)
    }

    @Test
    @DisplayName("검색 기록 저장 시 ID가 누락되면 예외를 던진다")
    fun recordSearchHistoryFailWhenIdNull() {
        val savedEntity = SearchHistoryEntity(
            id = null,
            memberId = 1L,
            query = "테스트",
            answer = "답변",
            referencedChunkIds = emptyList()
        )

        `when`(searchHistoryRepository.save(any())).thenReturn(savedEntity)

        assertThrows<BusinessException> {
            searchHistoryService.recordSearchHistory(
                memberId = 1L,
                query = "테스트",
                answer = "답변",
                referencedChunkIds = emptyList()
            )
        }
    }
}