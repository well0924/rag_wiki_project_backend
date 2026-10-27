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
    DUPLICATE_GROUP_NAME(HttpStatus.BAD_REQUEST,"유저 그룹이 중복이 됩니다."),

    // Document (새로 추가할 지점)
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 문서입니다."),
    DOCUMENT_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "문서 엔티티 저장에 실패했습니다."),
    UNAUTHORIZED_DOCUMENT_ACCESS(HttpStatus.FORBIDDEN,"접근 권한이 없는 기밀 문서입니다."),
    FORBIDDEN_DOCUMENT_ACCESS(HttpStatus.FORBIDDEN,"해당 그룹에 문서를 업로드하거나 접근할 권한이 없습니다."),
    UNSUPPORTED_DOCUMENT_TYPE(HttpStatus.BAD_REQUEST,"지원되지 않는 문서 형식입니다. (TXT, MD, PDF, DOCX만 가능)"),
    DOCUMENT_TEXT_EXTRACTION_FAILED(HttpStatus.BAD_REQUEST,"문서에서 텍스트를 추출할 수 없습니다. (손상된 파일 또는 이미지 스캔 PDF)"),
    DOCUMENT_INDEXING_REQUEST_FAILED(HttpStatus.SERVICE_UNAVAILABLE,"문서 인덱싱 요청에 실패했습니다. 잠시 후 다시 업로드해 주세요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND,"적절한 히스토리 없습니다."),
    FORBIDDEN_HISTORY_ACCESS(HttpStatus.FORBIDDEN,"본인의 검색 기록에만 피드백을 남길 수 있습니다."),
    ADMIN_ONLY(HttpStatus.FORBIDDEN,"관리자만 접근할 수 있습니다."),


    AI_SERVER_COMMUNICATION_FAILED(HttpStatus.BAD_GATEWAY
    ,"AI 통신에 문제가 있습니다.")
}