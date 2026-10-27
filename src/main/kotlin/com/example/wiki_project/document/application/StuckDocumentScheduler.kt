package com.example.wiki_project.document.application

import com.example.wiki_project.document.domain.model.DocumentStatus
import com.example.wiki_project.document.infrastructure.DocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

/**
 * 인덱싱 상태 콜백이 오지 않아 UPLOADED / PROCESSING 에 멈춘 문서를 주기적으로 FAILED 처리
 * - 사용자는 문서 목록에서 실패 상태를 확인하고 다시 업로드할 수 있음
 * - 늦게 도착한 워커 콜백(COMPLETED)은 FAILED 상태에서도 정상 반영됨
 */
@Component
class StuckDocumentScheduler(
    private val documentRepository: DocumentRepository,
    @Value("\${document.indexing.timeout:PT30M}") private val indexingTimeout: Duration
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${document.indexing.stuck-check-interval:PT10M}",
        initialDelayString = "\${document.indexing.stuck-check-interval:PT10M}"
    )
    @Transactional
    fun failStuckDocuments(): Int {
        val now = LocalDateTime.now()
        val failedCount = documentRepository.failStuckDocuments(
            stuckStatuses = listOf(DocumentStatus.UPLOADED, DocumentStatus.PROCESSING),
            failedStatus = DocumentStatus.FAILED,
            threshold = now.minus(indexingTimeout),
            now = now
        )
        if (failedCount > 0) {
            log.warn("인덱싱 시간 초과 문서 {}건 FAILED 처리 (기준: {})", failedCount, indexingTimeout)
        }
        return failedCount
    }
}
