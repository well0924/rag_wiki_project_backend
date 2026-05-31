package com.example.wiki_project.document.domain.entity

import com.example.wiki_project.document.domain.model.DocumentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "documents")
class DocumentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val title: String,

    @Column(name = "content_url", nullable = false, columnDefinition = "TEXT")
    val contentUrl: String, // S3 Object Key Path

    @Column(name = "doc_type")
    val docType: String,

    @Column(name = "file_size")
    val fileSize: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DocumentStatus = DocumentStatus.UPLOADED,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

    @Column(name = "created_by", nullable = false)
    val createdBy: Long,

    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)