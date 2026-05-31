package com.example.wiki_project.searchhistory.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.Type
import java.time.LocalDateTime
import io.hypersistence.utils.hibernate.type.array.ListArrayType
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name="search_history")
class SearchHistoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(nullable = false, columnDefinition = "TEXT")
    val query: String,

    @Column(columnDefinition = "TEXT")
    val answer: String?,

    // PostgreSQL 내 text[] 타입을 하이버네이트 6 사상에 맞춰 바인딩 유도
    @Type(ListArrayType::class)
    @Column(name = "referenced_chunk_ids", columnDefinition = "text[]")
    val referencedChunkIds: List<String> = emptyList(),

    @Column(nullable = true)
    var feedback: Boolean? = null, // 품질 측정을 위한 추천/비추천 토글 가드

    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)