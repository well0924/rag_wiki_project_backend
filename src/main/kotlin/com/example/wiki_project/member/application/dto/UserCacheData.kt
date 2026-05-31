package com.example.wiki_project.member.application.dto

import com.example.wiki_project.member.domain.model.MemberModel

data class UserCacheData(val member: MemberModel,
                         val groupIds: List<Long>,
                         val isAdmin: Boolean)
