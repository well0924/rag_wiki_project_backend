package com.example.wiki_project.member.domain.mapper

import com.example.wiki_project.member.domain.entity.MemberEntity
import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.model.MemberModel
import com.example.wiki_project.member.domain.model.Role
import com.example.wiki_project.member.domain.model.UserGroupMemberModel
import com.example.wiki_project.member.domain.model.UserGroupModel

fun MemberEntity.toModel(): MemberModel = MemberModel(
    id = this.id,
    email = this.email,
    password = this.password,
    userName = this.userName,
    role = Role.valueOf(this.role), // String -> Enum
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    isDeleted = this.isDeleted
)

fun MemberModel.toEntity(): MemberEntity = MemberEntity(
    id = this.id,
    email = this.email,
    password = this.password,
    userName = this.userName,
    role = this.role.name, // Enum -> String
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    isDeleted = this.isDeleted
)

// ==============================
// 2. UserGroup ↔ UserGroupModel
// ==============================
fun UserGroup.toModel(): UserGroupModel = UserGroupModel(
    id = this.id,
    groupName = this.groupName,
    description = this.description
)

fun UserGroupModel.toEntity(): UserGroup = UserGroup(
    id = this.id,
    groupName = this.groupName,
    description = this.description
)

// ==============================
// 3. UserGroupMember ↔ UserGroupMemberModel
// ==============================
fun UserGroupMember.toModel(): UserGroupMemberModel = UserGroupMemberModel(
    member = this.member.toModel(), // MemberEntity -> MemberModel
    group = this.group.toModel()    // UserGroupEntity -> UserGroupModel
)

fun UserGroupMemberModel.toEntity(): UserGroupMember = UserGroupMember(
    member = this.member.toEntity(),
    group = this.group.toEntity()
)
