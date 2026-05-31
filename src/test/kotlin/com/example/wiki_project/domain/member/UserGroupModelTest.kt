package com.example.wiki_project.domain.member

import com.example.wiki_project.member.domain.model.UserGroupModel
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UserGroupModelTest {

    @Test
    @DisplayName("그룹 이름과 설명을 정상적으로 수정할 수 있어야 한다")
    fun updateGroupInfoSuccess() {
        // given
        val group = UserGroupModel(id = 1L, groupName = "플랫폼개발팀", description = "인프라 및 코어 개발")
        val newGroupName = "플랫폼아키텍처팀"
        val newDescription = "전사 아키텍처 및 클라우드 통제"

        // when
        val updatedGroup = group.updateGroupInfo(newGroupName, newDescription)

        // then
        assertThat(updatedGroup.groupName).isEqualTo(newGroupName)
        assertThat(updatedGroup.description).isEqualTo(newDescription)
        assertThat(updatedGroup.id).isEqualTo(group.id)
    }

    @Test
    @DisplayName("그룹 정보를 수정할 때 새 이름이 공백이거나 빈 문자열이면 예외가 발생한다")
    fun updateGroupInfoFailBlankName() {
        // given
        val group = UserGroupModel(id = 1L, groupName = "보안운영팀", description = "전사 보안 가드")

        // when & then
        assertThatThrownBy { group.updateGroupInfo("   ", "설명수정") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("그룹 이름은 필수입니다.")
    }

    @Test
    @DisplayName("그룹 이름을 100자 초과하여 수정하려고 하면 예외가 발생한다")
    fun updateGroupInfoFailTooLongName() {
        // given
        val group = UserGroupModel(id = 1L, groupName = "인사팀", description = "HR")
        val tooLongGroupName = "G".repeat(101)

        // when & then
        assertThatThrownBy { group.updateGroupInfo(tooLongGroupName, "설명수정") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("그룹 이름은 100자를 초과할 수 없습니다.")
    }
}