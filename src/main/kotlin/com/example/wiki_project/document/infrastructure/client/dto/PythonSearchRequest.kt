package com.example.wiki_project.document.infrastructure.client.dto

data class PythonSearchRequest(
    val query: String,
    val userGroupIds: List<Long>,
    val isAdmin: Boolean
)
