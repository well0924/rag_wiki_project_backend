package com.example.wiki_project.common.batch

import com.example.wiki_project.document.domain.entity.DocumentEntity
import jakarta.persistence.EntityManagerFactory
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
    private val entityManagerFactory: EntityManagerFactory
) {

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
     * 1. READER : RDB에서 isDeleted = true 상태인 문서 메타데이터를 페이징 버퍼링 조회
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
     * 2. PROCESSOR : 나중에 WebClient 나 Kafka를 활용해서 Python Vector DB 진영에
     * "이 문서 ID에 해당하는 임베딩 벡터 데이터 다 파기해라" 하고 싱크 요청을 보내는 빌드업 지점!
     */
    @Bean
    fun vectorDbSyncProcessor(): ItemProcessor<DocumentEntity, DocumentEntity> {
        return ItemProcessor { document ->
            // TODO: webClient.post().uri("/api/v1/vector/clear/${document.id}").retrieve().awaitBody<Unit>()
            // 기획서 스펙의 데이터 정합성 이중화 문제를 해결하는 코어 엔진 라인!
            document
        }
    }

    /**
     * 3. WRITER : 외부 AI 진영 파기 처리가 안전하게 승인 완료된 녀석들을
     * 우리 RDB 영속성 컨텍스트에서도 완전히 지워내며(Hard Delete) 스토리지 용량을 클린징하는 마무리 투수!
     */
    @Bean
    fun rdbHardDeleteWriter(): ItemWriter<DocumentEntity> {
        return ItemWriter { chunk ->
            val entityManager = entityManagerFactory.createEntityManager()
            val transaction = entityManager.transaction

            try {
                transaction.begin()
                chunk.items.forEach { document ->
                    // 데이터 참조 무결성을 위해 영구 파기 수행
                    val managedDoc = entityManager.merge(document)
                    entityManager.remove(managedDoc)
                }
                transaction.commit()
            } catch (e: Exception) {
                transaction.rollback()
                throw e
            } finally {
                entityManager.close()
            }
        }
    }
}