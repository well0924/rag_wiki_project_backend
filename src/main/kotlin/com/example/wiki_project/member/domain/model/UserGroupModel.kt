package com.example.wiki_project.member.domain.model

class UserGroupModel(
    val id: Long? = null,
    val groupName: String,
    val description: String?
) {
    /**
     * 그룹 정보 수정 및 유효성 검증
     */
    fun updateGroupInfo(newGroupName: String, newDescription: String?): UserGroupModel {
        require(newGroupName.isNotBlank()) { "그룹 이름은 필수입니다." }
        require(newGroupName.length <= 100) { "그룹 이름은 100자를 초과할 수 없습니다." }

        return UserGroupModel(
            id = this.id,
            groupName = newGroupName,
            description = newDescription
        )
    }
}