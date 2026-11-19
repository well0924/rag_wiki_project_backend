package com.example.wiki_project.member.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "user_groups")
class UserGroup(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // 이름은 서비스 전체가 아니라 만든 사람(OWNER) 기준으로만 중복 불가 → DB 유니크 제약 없음 (GroupService에서 검사)
    // - ddl-auto=update 는 기존 유니크 제약을 지우지 않으므로, 운영 DB는 제약을 직접 삭제해야 함 (docs/decision-log.md)
    @Column(name = "group_name", nullable = false, length = 100)
    var groupName: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null
) {
}