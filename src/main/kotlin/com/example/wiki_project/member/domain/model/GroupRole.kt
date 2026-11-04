package com.example.wiki_project.member.domain.model

/**
 * 그룹(워크스페이스) 안에서의 역할
 * - OWNER: 그룹을 만든 사람. 그룹 수정 / 삭제, 멤버 추가 / 제거 가능
 * - MEMBER: OWNER가 추가한 사람. 문서 업로드와 검색만 가능
 */
enum class GroupRole {
    OWNER,
    MEMBER
}
