package com.example.wiki_project.document.infrastructure

import com.example.wiki_project.document.domain.entity.DocumentPermissionJpaEntity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface DocumentPermissionRepository: JpaRepository<DocumentPermissionJpaEntity, Long> {

    @Query("""
        SELECT COUNT(dp) > 0 
        FROM DocumentPermissionJpaEntity dp 
        WHERE dp.documentId = :documentId 
          AND dp.groupId IN :groupIds
    """) // 굳이 등급을 텍스트로 안 쪼개고, 매핑이 있는지만 확인해도 권한 필터가 작동해!
    fun hasReadPermission(
        @Param("documentId") documentId: Long,
        @Param("groupIds") groupIds: List<Long>
    ): Boolean
}