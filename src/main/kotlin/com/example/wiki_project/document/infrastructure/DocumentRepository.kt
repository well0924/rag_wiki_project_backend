package com.example.wiki_project.document.infrastructure

import com.example.wiki_project.document.domain.entity.DocumentEntity
import com.example.wiki_project.document.domain.model.DocumentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

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

    /**
     * 기준 시각 이전부터 인덱싱 대기/진행 상태에 멈춰 있는 문서를 일괄 FAILED 처리
     * (큐 메시지 유실, 워커 다운 등으로 상태 콜백이 끝내 오지 않는 경우 대비)
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE DocumentEntity d
        SET d.status = :failedStatus, d.updatedAt = :now
        WHERE d.isDeleted = false
        AND d.status IN :stuckStatuses
        AND d.updatedAt < :threshold
    """)
    fun failStuckDocuments(
        @Param("stuckStatuses") stuckStatuses: List<DocumentStatus>,
        @Param("failedStatus") failedStatus: DocumentStatus,
        @Param("threshold") threshold: LocalDateTime,
        @Param("now") now: LocalDateTime
    ): Int
}
