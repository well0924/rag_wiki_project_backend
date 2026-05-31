package com.example.wiki_project.member.application.dto

data class UserCreateCommand(
    val email: String,
    val password: String,
    val userName: String?
)
