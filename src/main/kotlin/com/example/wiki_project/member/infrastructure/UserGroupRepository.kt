package com.example.wiki_project.member.infrastructure

import com.example.wiki_project.member.domain.entity.UserGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserGroupRepository: JpaRepository<UserGroup, Long> {

}