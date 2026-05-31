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

    @Column(name = "group_name", nullable = false, unique = true, length = 100)
    var groupName: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null
) {
}