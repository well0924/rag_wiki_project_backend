package com.example.wiki_project.member.application.dto

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)
