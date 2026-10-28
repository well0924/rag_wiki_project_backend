package com.example.wiki_project.document.application.dto

import org.springframework.core.io.Resource

/**
 * 문서 다운로드 결과 (응답 파일명 + 원본 파일)
 */
data class DocumentDownload(
    val fileName: String,
    val resource: Resource
)
