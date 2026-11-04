package com.example.wiki_project.searchhistory.application.dto

/**
 * 관리자 품질 모니터링 지표
 * - 비율은 피드백(👍/👎)을 남긴 기록만 분모로 계산. 피드백이 한 건도 없으면 null (화면에서 "-"로 표시)
 */
data class SearchQualityStats(
    val totalSearches: Long,
    val positiveCount: Long,
    val negativeCount: Long
) {
    val feedbackCount: Long = positiveCount + negativeCount
    val positiveRate: Double? = rate(positiveCount)
    val negativeRate: Double? = rate(negativeCount)

    // 소수 첫째 자리까지의 백분율
    private fun rate(count: Long): Double? =
        if (feedbackCount == 0L) null else Math.round(count * 1000.0 / feedbackCount) / 10.0
}
