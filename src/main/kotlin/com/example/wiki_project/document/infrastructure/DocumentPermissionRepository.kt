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

    // 문서별 공개 그룹 이름 (요청자가 속한 그룹만)
    // - 검색 출처에 "어느 그룹 문서인지" 표시하는 용도
    // - 문서가 내가 속하지 않은 그룹에도 공개돼 있다면 그 그룹 이름은 내려주지 않음 (다른 그룹 정보 노출 방지)
    @Query("""
        SELECT dp.documentId AS documentId, g.groupName AS groupName
        FROM DocumentPermissionJpaEntity dp, UserGroup g
        WHERE g.id = dp.groupId
          AND dp.documentId IN :documentIds
          AND dp.groupId IN :groupIds
        ORDER BY g.groupName
    """)
    fun findGroupNamesForDocuments(
        @Param("documentIds") documentIds: List<Long>,
        @Param("groupIds") groupIds: List<Long>
    ): List<DocumentGroupName>
}

// 문서 ID + 공개 그룹 이름 조회 결과 (인터페이스 기반 프로젝션)
interface DocumentGroupName {
    val documentId: Long
    val groupName: String
}