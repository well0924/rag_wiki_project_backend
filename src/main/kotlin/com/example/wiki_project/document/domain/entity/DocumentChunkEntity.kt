package com.example.wiki_project.document.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "document_chunks")
class DocumentChunkEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val documentId: Long,

    @Column(nullable = false)
    val chunkIndex: Int,

    @Column(columnDefinition = "TEXT", nullable = false)
    val content: String,

    @Column(columnDefinition = "vector(512)")
    val embedding: String? = null
) {
    // 검색 결과를 생성할 때 사용할 간단한 변환 로직 정도만 추가
    fun toSummary(): String {
        return "Chunk ${chunkIndex}: ${content.take(20)}..."
    }
}