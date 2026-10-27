package com.example.wiki_project.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 스케줄러 공통 활성화 (배치 스케줄러 on/off 설정과 무관하게 동작해야 하므로 별도 분리)
 */
@Configuration
@EnableScheduling
class SchedulingConfig
