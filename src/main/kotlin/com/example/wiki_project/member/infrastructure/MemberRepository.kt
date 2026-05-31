package com.example.wiki_project.member.infrastructure

import com.example.wiki_project.member.domain.entity.MemberEntity
import jakarta.validation.constraints.Email
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberRepository: JpaRepository<MemberEntity,Long>{
    fun findByEmail(email: String): MemberEntity?
}