package com.example.wiki_project.document.domain.entity

import com.example.wiki_project.document.domain.model.PermissionLevel
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name="document_permissions")
class DocumentPermissionJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "document_id", nullable = false)
    val documentId: Long,

    @Column(name = "group_id", nullable = false)
    val groupId: Long,

    @Column(name = "permission_level", length = 20)
    @Enumerated(EnumType.STRING)
    val permissionLevel: PermissionLevel = PermissionLevel.READ
)