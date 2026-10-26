package com.example.wiki_project.domain.searchhistory

import com.example.wiki_project.document.infrastructure.client.PythonClientConfig
import com.example.wiki_project.document.infrastructure.client.dto.PythonSearchResponse
import com.example.wiki_project.searchhistory.application.SearchHistoryService
import com.example.wiki_project.searchhistory.application.SearchUsecase
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SearchUsecaseTest {

    private val pythonAiClient: PythonClientConfig = mock()
    private val searchHistoryService: SearchHistoryService = mock()
    private val searchUsecase = SearchUsecase(pythonAiClient, searchHistoryService)

    @Test
    @DisplayName("RAG 검색 시 유저 권한을 그대로 AI 서버에 전달하고, 저장된 기록 ID를 답변과 함께 반환한다")
    fun executeSearchReturnsHistoryId() = runTest {
        // given
        val groupIds = listOf(3L, 7L)
        val aiResponse = PythonSearchResponse(answer = "IdClass를 쓰시면 됩니다.", referencedChunkIds = listOf("11", "12"))
        org.mockito.kotlin.whenever(pythonAiClient.searchFromRAG("JPA 복합키", groupIds, false)).doReturn(aiResponse)
        org.mockito.kotlin.whenever(
            searchHistoryService.recordSearchHistory(10L, "JPA 복합키", aiResponse.answer, aiResponse.referencedChunkIds)
        ).doReturn(55L)

        // when
        val response = searchUsecase.executeSearch(memberId = 10L, query = "JPA 복합키", userGroupIds = groupIds, isAdmin = false)

        // then
        assertThat(response.historyId).isEqualTo(55L)
        assertThat(response.answer).isEqualTo("IdClass를 쓰시면 됩니다.")
        assertThat(response.referencedChunkIds).containsExactly("11", "12")
        verify(pythonAiClient).searchFromRAG("JPA 복합키", groupIds, false)
    }
}
