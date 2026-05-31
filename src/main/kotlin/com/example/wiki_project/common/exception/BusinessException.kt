package com.example.wiki_project.common.exception

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)