package com.example.wiki_project.common.batch

import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 논리 삭제된 문서의 영구 삭제 배치를 주기적으로 실행
 * - 기본: 매일 새벽 3시 (Asia/Seoul)
 * - batch.document-hard-delete.enabled=false 로 비활성화 가능
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["batch.document-hard-delete.enabled"], havingValue = "true", matchIfMissing = true)
class DocumentSyncBatchScheduler(
    private val jobLauncher: JobLauncher,
    @Qualifier(DocumentSyncBatchConfig.JOB_NAME) private val documentHardDeleteJob: Job
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${batch.document-hard-delete.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    fun runDocumentHardDeleteJob() {
        // 같은 파라미터로는 JobInstance가 재실행되지 않으므로 실행 시각을 파라미터로 부여
        val jobParameters = JobParametersBuilder()
            .addLong("requestedAt", System.currentTimeMillis())
            .toJobParameters()

        try {
            val execution = jobLauncher.run(documentHardDeleteJob, jobParameters)
            log.info("문서 영구 삭제 배치 종료: status={}", execution.status)
        } catch (e: Exception) {
            log.error("문서 영구 삭제 배치 실행 실패", e)
        }
    }
}
