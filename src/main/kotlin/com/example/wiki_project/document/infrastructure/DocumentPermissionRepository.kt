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
    """) // 권한 등급 없이 문서-그룹 매핑 존재 여부만으로 접근 권한 판단
    fun hasReadPermission(
        @Param("documentId") documentId: Long,
        @Param("groupIds") groupIds: List<Long>
    ): Boolean
}