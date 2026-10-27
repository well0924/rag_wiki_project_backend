package com.example.wiki_project.domain.document

import com.example.wiki_project.common.batch.DocumentSyncBatchConfig
import com.example.wiki_project.document.domain.entity.DocumentEntity
import com.example.wiki_project.document.domain.entity.DocumentPermissionJpaEntity
import com.example.wiki_project.document.infrastructure.DocumentPermissionRepository
import com.example.wiki_project.document.infrastructure.DocumentRepository
import com.example.wiki_project.document.infrastructure.client.PythonClientConfig
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(properties = ["batch.document-hard-delete.enabled=false"])
class DocumentHardDeleteJobTest {

    @Autowired
    private lateinit var jobLauncher: JobLauncher

    @Autowired
    @Qualifier(DocumentSyncBatchConfig.JOB_NAME)
    private lateinit var documentHardDeleteJob: Job

    @Autowired
    private lateinit var documentRepository: DocumentRepository

    @Autowired
    private lateinit var documentPermissionRepository: DocumentPermissionRepository

    // 실제 FastAPI 서버 대신 벡터 파기 요청만 기록
    @MockitoBean
    private lateinit var pythonClientConfig: PythonClientConfig

    private val createdIds = mutableListOf<Long>()

    @AfterEach
    fun cleanUp() {
        documentPermissionRepository.deleteAll(documentPermissionRepository.findAll().filter { it.documentId in createdIds })
        documentRepository.deleteAllById(documentRepository.findAllById(createdIds).mapNotNull { it.id })
    }

    private fun saveDocuments(count: Int, isDeleted: Boolean): List<Long> {
        val saved = documentRepository.saveAll(
            (1..count).map {
                DocumentEntity(
                    title = "batch-test-$it",
                    contentUrl = "batch-test-url-$it",
                    docType = "TXT",
                    fileSize = 1L,
                    isDeleted = isDeleted,
                    createdBy = 1L
                )
            }
        ).mapNotNull { it.id }
        createdIds += saved
        return saved
    }

    @Test
    @DisplayName("청크 크기(100)를 넘는 삭제 문서도 페이지 건너뜀 없이 권한 매핑까지 전부 영구 삭제된다")
    fun hardDeleteAllSoftDeletedDocuments() {
        val deletedIds = saveDocuments(250, isDeleted = true)
        val aliveIds = saveDocuments(2, isDeleted = false)
        documentPermissionRepository.saveAll(deletedIds.map { DocumentPermissionJpaEntity(documentId = it, groupId = 1L) })

        val execution = jobLauncher.run(
            documentHardDeleteJob,
            JobParametersBuilder().addLong("requestedAt", System.currentTimeMillis()).toJobParameters()
        )

        assertThat(execution.status).isEqualTo(BatchStatus.COMPLETED)
        assertThat(documentRepository.findAllById(deletedIds)).isEmpty()
        assertThat(documentRepository.findAllById(aliveIds)).hasSize(2)
        assertThat(documentPermissionRepository.findAll().filter { it.documentId in deletedIds }).isEmpty()
        runBlocking { verify(pythonClientConfig, times(250)).clearVectorChunks(any()) }
    }
}
