package com.example.wiki_project.member.application.dto

data class CreateGroupRequest(
    val groupName: String,
    val description: String?
)