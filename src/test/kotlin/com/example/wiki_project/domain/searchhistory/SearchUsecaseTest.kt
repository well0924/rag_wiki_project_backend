package com.example.wiki_project.domain.searchhistory

import com.example.wiki_project.document.infrastructure.DocumentGroupName
import com.example.wiki_project.document.infrastructure.DocumentPermissionRepository
import com.example.wiki_project.document.infrastructure.client.PythonClientConfig
import com.example.wiki_project.document.infrastructure.client.dto.PythonSearchResponse
import com.example.wiki_project.document.infrastructure.client.dto.PythonSearchSource
import com.example.wiki_project.searchhistory.application.SearchHistoryService
import com.example.wiki_project.searchhistory.application.SearchUsecase
import com.example.wiki_project.searchhistory.application.dto.SearchSource
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SearchUsecaseTest {

    private val pythonAiClient: PythonClientConfig = mock()
    private val searchHistoryService: SearchHistoryService = mock()
    private val documentPermissionRepository: DocumentPermissionRepository = mock()
    private val searchUsecase = SearchUsecase(pythonAiClient, searchHistoryService, documentPermissionRepository)

    @Test
    @DisplayName("RAG 검색 시 유저 권한을 그대로 AI 서버에 전달하고, 저장된 기록 ID를 답변과 함께 반환한다")
    fun executeSearchReturnsHistoryId() = runTest {
        // given
        val groupIds = listOf(3L, 7L)
        val aiResponse = PythonSearchResponse(
            answer = "IdClass를 쓰시면 됩니다. [1]",
            referencedChunkIds = listOf("11", "12"),
            sources = listOf(PythonSearchSource(documentId = 5L, title = "JPA 가이드", similarity = 0.72))
        )
        whenever(pythonAiClient.searchFromRAG("JPA 복합키", groupIds)).doReturn(aiResponse)
        whenever(
            searchHistoryService.recordSearchHistory(10L, "JPA 복합키", aiResponse.answer, aiResponse.referencedChunkIds)
        ).doReturn(55L)
        whenever(documentPermissionRepository.findGroupNamesForDocuments(listOf(5L), groupIds))
            .doReturn(listOf(groupName(5L, "스프링 스터디")))

        // when
        val response = searchUsecase.executeSearch(memberId = 10L, query = "JPA 복합키", userGroupIds = groupIds)

        // then
        assertThat(response.historyId).isEqualTo(55L)
        assertThat(response.answer).isEqualTo("IdClass를 쓰시면 됩니다. [1]")
        assertThat(response.referencedChunkIds).containsExactly("11", "12")
        assertThat(response.sources).containsExactly(
            SearchSource(documentId = 5L, title = "JPA 가이드", similarity = 0.72, groupNames = listOf("스프링 스터디"))
        )
        verify(pythonAiClient).searchFromRAG("JPA 복합키", groupIds)
    }

    @Test
    @DisplayName("출처마다 내가 속한 공개 그룹 이름을 붙이고, 여러 그룹에 공개된 문서는 이름을 모두 붙인다")
    fun executeSearchAttachesGroupNamesPerSource() = runTest {
        // given
        val groupIds = listOf(1L, 2L)
        val aiResponse = PythonSearchResponse(
            answer = "답변 [1][2]",
            sources = listOf(
                PythonSearchSource(documentId = 10L, title = "연차 휴가 규정", similarity = 0.8),
                PythonSearchSource(documentId = 20L, title = "서비스 배포 절차", similarity = 0.6)
            )
        )
        whenever(pythonAiClient.searchFromRAG("질문", groupIds)).doReturn(aiResponse)
        whenever(searchHistoryService.recordSearchHistory(any(), any(), any(), any())).doReturn(1L)
        whenever(documentPermissionRepository.findGroupNamesForDocuments(listOf(10L, 20L), groupIds))
            .doReturn(
                listOf(
                    groupName(10L, "경영지원팀"),
                    groupName(20L, "경영지원팀"),
                    groupName(20L, "플랫폼개발팀")
                )
            )

        // when
        val response = searchUsecase.executeSearch(memberId = 1L, query = "질문", userGroupIds = groupIds)

        // then
        assertThat(response.sources.map { it.groupNames }).containsExactly(
            listOf("경영지원팀"),
            listOf("경영지원팀", "플랫폼개발팀")
        )
    }

    @Test
    @DisplayName("근거 문서가 없으면 그룹 이름을 조회하지 않는다")
    fun executeSearchSkipsGroupLookupWithoutSources() = runTest {
        // given
        val groupIds = listOf(1L)
        whenever(pythonAiClient.searchFromRAG("주차 규정", groupIds))
            .doReturn(PythonSearchResponse(answer = "찾을 수 없습니다."))
        whenever(searchHistoryService.recordSearchHistory(any(), any(), any(), any())).doReturn(1L)

        // when
        val response = searchUsecase.executeSearch(memberId = 1L, query = "주차 규정", userGroupIds = groupIds)

        // then
        assertThat(response.sources).isEmpty()
        verify(documentPermissionRepository, never()).findGroupNamesForDocuments(any(), any())
    }

    private fun groupName(documentId: Long, groupName: String) = object : DocumentGroupName {
        override val documentId = documentId
        override val groupName = groupName
    }
}
