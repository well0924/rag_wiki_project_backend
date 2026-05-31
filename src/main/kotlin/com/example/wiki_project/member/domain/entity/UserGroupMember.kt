package com.example.wiki_project.member.domain.entity

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable
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
    val group: UserGroup
) {

    override fun equals(other: Any?): Boolean {
        if (other !is UserGroupMember) return false
        return member.id == other.member.id && group.id == other.group.id
    }

    override fun hashCode() = Objects.hash(member.id, group.id)
}