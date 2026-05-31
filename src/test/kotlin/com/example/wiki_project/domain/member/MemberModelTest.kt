package com.example.wiki_project.domain.member

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MemberModelTest {

    private fun createDefaultMember(): MemberModel {
        return MemberModel(
            id = 1L,
            email = "dev@company.com",
            password = "encrypted_password_123",
            userName = "홍길동",
            role = Role.ROLE_MEMBER,
            isDeleted = false
        )
    }

    @Test
    @DisplayName("유저 이름을 변경하면 새로운 이름과 갱신된 수정 시각을 가진 복사본이 반환되어야 한다")
    fun changeUserNameSuccess() {
        // given
        val member = createDefaultMember()
        val newName = "이순신"

        // when
        val updatedMember = member.changeUserName(newName)

        // then
        assertThat(updatedMember.userName).isEqualTo(newName)
        assertThat(updatedMember.id).isEqualTo(member.id)
        assertThat(updatedMember.updatedAt).isAfterOrEqualTo(member.updatedAt)
    }

    @Test
    @DisplayName("유저 이름을 공백이나 빈 문자열로 변경 시 예외가 발생한다")
    fun changeUserNameFailBlank() {
        // given
        val member = createDefaultMember()

        // when & then
        assertThatThrownBy { member.changeUserName("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("유저 이름은 공백일 수 없습니다.")
    }

    @Test
    @DisplayName("유저 이름이 100자를 초과하면 예외가 발생한다")
    fun changeUserNameFailLength() {
        // given
        val member = createDefaultMember()
        val tooLongName = "A".repeat(101)

        // when & then
        assertThatThrownBy { member.changeUserName(tooLongName) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("유저 이름은 100자를 초과할 수 없습니다.")
    }

    @Test
    @DisplayName("일반 회원을 관리자로 정상 승격할 수 있어야 한다")
    fun promoteToAdminSuccess() {
        // given
        val member = createDefaultMember()

        // when
        val adminMember = member.promoteToAdmin()

        // then
        assertThat(adminMember.role).isEqualTo(Role.ROLE_ADMIN)
    }

    @Test
    @DisplayName("이미 관리자인 회원을 다시 승격시키려고 하면 예외가 발생한다")
    fun promoteToAdminFailAlreadyAdmin() {
        // given
        val adminMember = MemberModel(
            id = 2L,
            email = "admin@company.com",
            password = "password",
            userName = "관리자",
            role = Role.ROLE_ADMIN
        )

        // when & then
        assertThatThrownBy { adminMember.promoteToAdmin() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("이미 관리자 권한을 가진 회원입니다.")
    }

    @Test
    @DisplayName("회원 프로필 수정 시 유효한 이름으로 정상 업데이트되어야 한다")
    fun updateProfileSuccess() {
        // given
        val member = createDefaultMember()
        val newName = "임꺽정"

        // when
        val updatedMember = member.updateProfile(newName)

        // then
        assertThat(updatedMember.userName).isEqualTo(newName)
        assertThat(updatedMember.password).isEqualTo(member.password) // 비밀번호 유지 확인
    }

    @Test
    @DisplayName("회원 프로필 수정 시 변경할 이름이 공백이면 예외가 발생한다")
    fun updateProfileFailBlank() {
        // given
        val member = createDefaultMember()

        // when & then
        assertThatThrownBy { member.updateProfile("") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("변경할 이름은 공백일 수 없습니다.")
    }

    @Test
    @DisplayName("회원 탈퇴 처리 시 isDeleted 플래그가 true로 변경되어야 한다")
    fun withdrawSuccess() {
        // given
        val member = createDefaultMember()

        // when
        val withdrawnMember = member.withdraw()

        // then
        assertThat(withdrawnMember.isDeleted).isTrue()
    }

    @Test
    @DisplayName("이미 탈퇴 처리된 회원이 다시 탈퇴를 시도하면 예외가 발생한다")
    fun withdrawFailAlreadyDeleted() {
        // given
        val alreadyDeletedMember = MemberModel(
            id = 3L,
            email = "deleted@company.com",
            password = "password",
            userName = "탈퇴자",
            isDeleted = true
        )

        // when & then
        assertThatThrownBy { alreadyDeletedMember.withdraw() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("이미 탈퇴한 회원입니다.")
    }

    @Test
    @DisplayName("비밀번호 변경 시 새로운 암호화 패스워드로 정상 교체되어야 한다")
    fun changePasswordSuccess() {
        // given
        val member = createDefaultMember()
        val newPassword = "new_encrypted_password_567"

        // when
        val updatedMember = member.changePassword(newPassword)

        // then
        assertThat(updatedMember.password).isEqualTo(newPassword)
        assertThat(updatedMember.userName).isEqualTo(member.userName) // 다른 필드 보존 확인
    }
}