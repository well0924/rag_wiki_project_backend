package com.example.wiki_project.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val message: String) {
    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "올바르지 않은 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // Member / Auth
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 존재하는 이메일입니다."),
    ALREADY_WITHDRAWN_MEMBER(HttpStatus.NOT_FOUND, "이미 탈퇴를 한 회원입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 자격 증명이 유효하지 않습니다."),

    // User Group
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저 그룹입니다."),
    ALREADY_JOINED_GROUP(HttpStatus.BAD_REQUEST,"이미 가입된 그룹입니다."),
    ADMIN_CANNOT_BE_ASSIGNED_TO_GROUP(HttpStatus.BAD_REQUEST,"관리자는 그룹을 할당을 할 수 없습니다."),

    // Document (새로 추가할 지점)
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 문서입니다."),
    DOCUMENT_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "문서 엔티티 저장에 실패했습니다."),
    UNAUTHORIZED_DOCUMENT_ACCESS(HttpStatus.UNAUTHORIZED,"접근 권한이 없는 기밀 문서입니다."),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND,"적절한 히스토리 없습니다."),


    AI_SERVER_COMMUNICATION_FAILED(HttpStatus.REQUEST_TIMEOUT
    ,"AI 통신에 문제가 있습니다.")
}