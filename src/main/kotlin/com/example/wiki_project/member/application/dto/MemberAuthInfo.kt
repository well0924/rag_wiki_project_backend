package com.example.wiki_project.member.application.dto

data class MemberAuthInfo(
    val groupIds: List<Long>,
    val isAdmin: Boolean
)
