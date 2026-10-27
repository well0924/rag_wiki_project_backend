package com.example.wiki_project.domain.document

import com.example.wiki_project.document.application.StuckDocumentScheduler
import com.example.wiki_project.document.domain.entity.DocumentEntity
import com.example.wiki_project.document.domain.model.DocumentStatus
import com.example.wiki_project.document.infrastructure.DocumentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest(properties = ["batch.document-hard-delete.enabled=false"])
class StuckDocumentSchedulerTest {

    @Autowired
    private lateinit var stuckDocumentScheduler: StuckDocumentScheduler

    @Autowired
    private lateinit var documentRepository: DocumentRepository

    private val createdIds = mutableListOf<Long>()

    @AfterEach
    fun cleanUp() {
        documentRepository.deleteAllById(createdIds)
    }

    private fun saveDocument(status: DocumentStatus, updatedAt: LocalDateTime, isDeleted: Boolean = false): Long {
        val saved = documentRepository.save(
            DocumentEntity(
                title = "stuck-test-$status",
                contentUrl = "stuck-test-url",
                docType = "TXT",
                fileSize = 1L,
                status = status,
                isDeleted = isDeleted,
                createdBy = 1L,
                updatedAt = updatedAt
            )
        )
        return saved.id!!.also { createdIds += it }
    }

    private fun statusOf(id: Long) = documentRepository.findById(id).orElseThrow().status

    @Test
    @DisplayName("시간 초과된 UPLOADED / PROCESSING 문서만 FAILED 처리되고, 최근 문서나 완료/삭제 문서는 그대로 둔다")
    fun failOnlyStuckDocuments() {
        val longAgo = LocalDateTime.now().minusHours(2)
        val stuckUploaded = saveDocument(DocumentStatus.UPLOADED, longAgo)
        val stuckProcessing = saveDocument(DocumentStatus.PROCESSING, longAgo)
        val recentUploaded = saveDocument(DocumentStatus.UPLOADED, LocalDateTime.now())
        val oldCompleted = saveDocument(DocumentStatus.COMPLETED, longAgo)
        val deletedUploaded = saveDocument(DocumentStatus.UPLOADED, longAgo, isDeleted = true)

        stuckDocumentScheduler.failStuckDocuments()

        assertThat(statusOf(stuckUploaded)).isEqualTo(DocumentStatus.FAILED)
        assertThat(statusOf(stuckProcessing)).isEqualTo(DocumentStatus.FAILED)
        assertThat(statusOf(recentUploaded)).isEqualTo(DocumentStatus.UPLOADED)
        assertThat(statusOf(oldCompleted)).isEqualTo(DocumentStatus.COMPLETED)
        assertThat(statusOf(deletedUploaded)).isEqualTo(DocumentStatus.UPLOADED)
    }
}
