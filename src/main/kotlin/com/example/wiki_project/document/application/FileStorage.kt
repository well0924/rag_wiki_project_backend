package com.example.wiki_project.document.application

import org.springframework.core.io.Resource

/**
 * 문서 원본 파일 저장소
 * - key: 저장소 내부 경로 (예: documents/{uuid}.pdf) → documents.content_url 에 저장
 * - 현재는 로컬 디스크 구현(LocalFileStorage), S3 도입 시 구현체만 교체 (key 는 S3 object key 로 그대로 사용)
 */
interface FileStorage {

    fun store(key: String, bytes: ByteArray)

    /** 파일이 없거나 저장소 key 형식이 아니면 null (S3 도입 전 더미 URL 로 저장된 문서 포함) */
    fun load(key: String): Resource?

    /** 파일이 없으면 무시 (배치 재실행 시 이미 지워진 파일 허용) */
    fun delete(key: String)
}
