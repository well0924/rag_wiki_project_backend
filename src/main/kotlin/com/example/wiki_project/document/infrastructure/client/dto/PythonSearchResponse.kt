package com.example.wiki_project.document.infrastructure.client.dto

data class PythonSearchResponse(
    val answer: String,
    val referencedChunkIds: List<String> = emptyList()
)