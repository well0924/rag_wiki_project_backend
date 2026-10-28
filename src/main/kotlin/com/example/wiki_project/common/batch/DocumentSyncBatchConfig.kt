package com.example.wiki_project.common.batch

import com.example.wiki_project.document.application.FileStorage
import com.example.wiki_project.document.domain.entity.DocumentEntity
import com.example.wiki_project.document.infrastructure.client.PythonClientConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceContext
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.database.JpaPagingItemReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class DocumentSyncBatchConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val entityManagerFactory: EntityManagerFactory,
    private val pythonClientConfig: PythonClientConfig,
    private val fileStorage: FileStorage
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object {
        const val JOB_NAME = "documentHardDeleteJob"
        private const val CHUNK_SIZE = 100 // 한 트랜잭션에서 처리할 문서 수 (메모리 사용량 제한)
    }

    @Bean
    fun documentHardDeleteJob(documentClearStep: Step): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .start(documentClearStep)
            .build()
    }

    @Bean
    fun documentClearStep(): Step {
        return StepBuilder("documentClearStep", jobRepository)
            .chunk<DocumentEntity, DocumentEntity>(CHUNK_SIZE, transactionManager)
            .reader(deletedDocumentReader())
            .processor(vectorDbSyncProcessor())
            .writer(rdbHardDeleteWriter())
            .build()
    }

    /**
     *  READER : isDeleted = true 인 문서를 페이지 단위로 조회
     *  - 읽은 행을 writer가 바로 삭제하므로, 일반 페이징(offset 증가)을 쓰면
     *    2페이지부터 앞당겨진 행을 건너뛰게 됨 → 항상 첫 페이지(offset 0)만 다시 조회
     */
    @Bean
    @StepScope // 스텝 실행마다 새 리더 생성 (싱글톤 재사용/미오픈 상태 close 방지)
    fun deletedDocumentReader(): JpaPagingItemReader<DocumentEntity> {
        val reader = object : JpaPagingItemReader<DocumentEntity>() {
            override fun getPage(): Int = 0
        }
        reader.name = "deletedDocumentReader"
        reader.setEntityManagerFactory(entityManagerFactory)
        reader.setQueryString("SELECT d FROM DocumentEntity d WHERE d.isDeleted = true ORDER BY d.id")
        reader.pageSize = CHUNK_SIZE
        // 재시작 시 저장된 read.count 만큼 건너뛰면 남은 문서를 놓치므로 상태 저장 비활성화
        reader.isSaveState = false
        return reader
    }

    /**
     *  PROCESSOR : AI 서버에 해당 문서의 벡터 청크 삭제를 요청한 뒤 원본 파일 삭제
     * - 실패 시 예외가 전파되어 해당 청크는 롤백되고, 다음 배치 실행 때 다시 시도됨
     */
    @Bean
    fun vectorDbSyncProcessor(): ItemProcessor<DocumentEntity, DocumentEntity> {
        return ItemProcessor { document ->
            val documentId = requireNotNull(document.id)

            runBlocking {
                pythonClientConfig.clearVectorChunks(documentId)
            }
            // 원본 파일 삭제 (이미 없으면 무시 → 이후 RDB 삭제가 실패해 재실행되어도 안전)
            fileStorage.delete(document.contentUrl)

            log.info("Document ID: {} 벡터 청크 및 원본 파일 파기 완료", documentId)
            document
        }
    }

    /**
     *  WRITER : 벡터 청크와 원본 파일 삭제가 끝난 문서를 RDB에서 영구 삭제
     * - 문서 권한 매핑(document_permissions)도 함께 지워 고아 데이터를 남기지 않음
     */
    @Bean
    fun rdbHardDeleteWriter(): ItemWriter<DocumentEntity> {
        return ItemWriter { chunk ->
            val documentIds = chunk.items.mapNotNull { it.id }
            if (documentIds.isEmpty()) return@ItemWriter

            // 배치의 PlatformTransactionManager 청크 트랜잭션 안에서 벌크 삭제
            entityManager.createQuery("DELETE FROM DocumentPermissionJpaEntity p WHERE p.documentId IN :ids")
                .setParameter("ids", documentIds)
                .executeUpdate()
            entityManager.createQuery("DELETE FROM DocumentEntity d WHERE d.id IN :ids")
                .setParameter("ids", documentIds)
                .executeUpdate()

            log.info("RDB 문서 {}건 영구 삭제 완료", documentIds.size)
        }
    }
}
