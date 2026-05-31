package com.example.wiki_project.member.domain.entity

import java.io.Serializable
import java.util.Objects

/**
 * UserGroupMember 엔티티의 다대다(M:N) 매핑을 위한 복합키 식별자 클래스 (@IdClass)
 * * [성능 최적화 포인트]
 * - 복합키 클래스 내부의 필드 선언 순서는 DB 테이블 생성 시 복합 PRIMARY KEY 컬럼 순서를 결정함.
 * - member(user_id)를 첫 번째 순서로 배치하여 PostgreSQL에서 PRIMARY KEY (user_id, group_id) 순으로 인덱스가 생성되도록 유도.
 * - 이 설정을 통해 user_id 단일 조건 조회 시에도 추가 인덱스 없이 PK B-Tree 인덱스를 효율적으로 활용(Index Scan)할 수 있음.
 */
class UserGroupMemberId(
    var member: Long? = null, // 엔티티의 @Id 필드명과 매칭 (기존 user에서 member로 변경)
    var group: Long? = null   // 엔티티의 @Id 필드명과 매칭
) : Serializable {

    // @IdClass는 영속성 컨텍스트에서 엔티티를 식별하기 위해 반드시 동등성(equals/hashCode) 재정의가 필요함
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserGroupMemberId) return false
        return member == other.member && group == other.group
    }

    override fun hashCode(): Int {
        return Objects.hash(member, group)
    }
}