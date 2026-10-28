package com.example.wiki_project.member.domain.model

import java.time.LocalDateTime

data class MemberModel(
    val id: Long? = null,
    val email: String,
    val password: String,
    val userName: String?,
    val role: Role = Role.ROLE_MEMBER,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val isDeleted: Boolean = false
) {
    /**
     *  유저 이름 변경 및 검증 로직
     * 상태를 직접 변경하지 않고, 코틀린의 불변성을 살려 복사본(copy)을 반환함.
     */
    fun changeUserName(newName: String): MemberModel {
        // 검증 규칙은 도메인 모델이 직접 관리
        require(newName.isNotBlank()) { "유저 이름은 공백일 수 없습니다." }
        require(newName.length <= 100) { "유저 이름은 100자를 초과할 수 없습니다." }

        return MemberModel(
            id = this.id,
            email = this.email,
            password = this.password,
            userName = newName,
            role = this.role,
            createdAt = this.createdAt,
            updatedAt = LocalDateTime.now() // 수정 시각 갱신
        )
    }

    /**
     * 관리자 권한 승격 로직
     */
    fun promoteToAdmin(): MemberModel {
        check(this.role != Role.ROLE_ADMIN) { "이미 관리자 권한을 가진 회원입니다." }

        return MemberModel(
            id = this.id,
            email = this.email,
            password = this.password,
            userName = this.userName,
            role = Role.ROLE_ADMIN,
            createdAt = this.createdAt,
            updatedAt = LocalDateTime.now()
        )
    }

    /**
     * 회원 정보 통합 수정 비즈니스 로직
     */
    fun updateProfile(newUserName: String?): MemberModel {
        // 회원 수정 시 거쳐야 할 비즈니스 검증 규칙 (예: 공백 검증 등)
        newUserName?.let {
            require(it.isNotBlank()) { "변경할 이름은 공백일 수 없습니다." }
            require(it.length <= 100) { "이름은 100자를 초과할 수 없습니다." }
        }

        return MemberModel(
            id = this.id,
            email = this.email,
            password = this.password, // 비밀번호는 changePassword 에서만 변경
            userName = newUserName,
            role = this.role,
            createdAt = this.createdAt,
            updatedAt = LocalDateTime.now(),
            isDeleted = this.isDeleted
        )
    }

    /**
     * 회원 탈퇴(Soft Delete) 비즈니스 로직
     */
    fun withdraw(): MemberModel {
        check(!this.isDeleted) { "이미 탈퇴한 회원입니다." }

        return MemberModel(
            id = this.id,
            email = this.email,
            password = this.password,
            userName = this.userName,
            role = this.role,
            createdAt = this.createdAt,
            updatedAt = LocalDateTime.now(),
            isDeleted = true
        )
    }

    fun changePassword(newEncryptedPassword: String): MemberModel {
        return MemberModel(
            id = this.id,
            email = this.email,
            password = newEncryptedPassword, // 암호화된 값만 전달받음
            userName = this.userName,
            role = this.role,
            createdAt = this.createdAt,
            updatedAt = LocalDateTime.now(),
            isDeleted = this.isDeleted
        )
    }
}