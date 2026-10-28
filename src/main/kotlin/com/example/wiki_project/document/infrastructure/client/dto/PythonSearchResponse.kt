package com.example.wiki_project.document.infrastructure.client.dto

data class PythonSearchResponse(
    val answer: String,
    val referencedChunkIds: List<String> = emptyList(),
    // 답변 근거 문서 (AI 서버가 LLM이 실제로 인용한 문서만 반환, 관련 문서가 없으면 빈 목록)
    val sources: List<PythonSearchSource> = emptyList()
)

data class PythonSearchSource(
    val documentId: Long,
    val title: String,
    val similarity: Double
)
