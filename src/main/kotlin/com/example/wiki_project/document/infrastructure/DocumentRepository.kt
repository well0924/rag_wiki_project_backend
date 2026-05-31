package com.example.wiki_project.document.infrastructure

import com.example.wiki_project.document.domain.entity.DocumentEntity
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DocumentRepository: JpaRepository<DocumentEntity, Long> {

    @Query("""
        SELECT DISTINCT d FROM DocumentEntity d 
        LEFT JOIN DocumentPermissionJpaEntity p ON d.id = p.documentId
        WHERE d.isDeleted = false
        AND (:isAdmin = true OR p.groupId IN :groupIds)
    """)
    fun findAccessibleDocuments(
        @Param("groupIds") groupIds: List<Long>,
        @Param("isAdmin") isAdmin: Boolean
    ): List<DocumentEntity>
}