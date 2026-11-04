package com.example.wiki_project.member.domain.entity

import com.example.wiki_project.member.domain.model.GroupRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Objects

@Entity
@Table(name = "user_group_members")
@IdClass(UserGroupMemberId::class)
class UserGroupMember(

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val member: MemberEntity,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    val group: UserGroup,

    // 기존 행이 있는 테이블에 컬럼을 추가할 때 NOT NULL 위반이 나지 않도록 DB 기본값을 MEMBER로 둠
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, columnDefinition = "varchar(20) default 'MEMBER' not null")
    val role: GroupRole = GroupRole.MEMBER
) {

    override fun equals(other: Any?): Boolean {
        if (other !is UserGroupMember) return false
        return member.id == other.member.id && group.id == other.group.id
    }

    override fun hashCode() = Objects.hash(member.id, group.id)
}
