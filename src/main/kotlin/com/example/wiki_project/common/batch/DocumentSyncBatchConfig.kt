package com.example.wiki_project.common.batch

import com.example.wiki_project.document.domain.entity.DocumentEntity
import com.example.wiki_project.document.infrastructure.client.PythonClientConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceContext
import kotlinx.coroutines.runBlocking
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.database.JpaPagingItemReader
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class DocumentSyncBatchConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val entityManagerFactory: EntityManagerFactory,
    private val pythonClientConfig: PythonClientConfig
) {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object {
        private const val CHUNK_SIZE = 100 // 대용량 메모리 부하를 고려한 청크 트랜잭션 단위 셋업
    }

    @Bean
    fun documentHardDeleteJob(documentClearStep: Step): Job {
        return JobBuilder("documentHardDeleteJob", jobRepository)
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
     *  READER : RDB에서 isDeleted = true 상태인 문서 메타데이터를 페이징 버퍼링 조회
     */
    @Bean
    fun deletedDocumentReader(): JpaPagingItemReader<DocumentEntity> {
        return JpaPagingItemReaderBuilder<DocumentEntity>()
            .name("deletedDocumentReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("SELECT d FROM DocumentEntity d WHERE d.isDeleted = true")
            .pageSize(CHUNK_SIZE)
            .build()
    }

    /**
     *  PROCESSOR : 나중에 WebClient 를 활용해서 Python Vector DB 진영에
     * "이 문서 ID에 해당하는 임베딩 벡터 데이터 다 파기해라" 하고 싱크 요청
     */
    @Bean
    fun vectorDbSyncProcessor(): ItemProcessor<DocumentEntity, DocumentEntity> {
        return ItemProcessor { document ->
            val documentId = document.id ?: return@ItemProcessor document

            runBlocking {
                pythonClientConfig.clearVectorChunks(documentId)
            }

            println("Document ID: $documentId 에 대한 파이썬 벡터 청크 파기 승인 완료.")
            document
        }
    }

    /**
     *  WRITER : 외부 AI 진영 파기 처리가 안전하게 승인 완료된 녀석들을
     * 우리 RDB 영속성 컨텍스트에서도 완전히 지워내기.
     */
    @Bean
    fun rdbHardDeleteWriter(): ItemWriter<DocumentEntity> {
        return ItemWriter { chunk ->
            //수동 transaction.begin() / commit() 찌꺼기를 완전히 걷어내고
            // 배치의PlatformTransactionManager 생명주기 안에서 안전하게 merge 및 remove 처리
            chunk.items.forEach { document ->
                val managedDoc = if (entityManager.contains(document)) {
                    document
                } else {
                    entityManager.merge(document)
                }
                entityManager.remove(managedDoc)
            }
            // 청크 루프가 끝나면 배치가 알아서 플러시(Flush) 및 커밋을 때려줌!
        }
    }
}