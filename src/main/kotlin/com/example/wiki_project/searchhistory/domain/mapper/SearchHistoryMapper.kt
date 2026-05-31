package com.example.wiki_project.searchhistory.domain.mapper

import com.example.wiki_project.searchhistory.domain.entity.SearchHistoryEntity
import com.example.wiki_project.searchhistory.domain.model.SearchHistoryModel

fun SearchHistoryEntity.toModel(): SearchHistoryModel {
    return SearchHistoryModel(
        id = this.id,
        memberId = this.memberId,
        query = this.query,
        answer = this.answer,
        referencedChunkIds = this.referencedChunkIds,
        feedback = this.feedback,
        createdAt = this.createdAt
    )
}

fun SearchHistoryModel.toEntity(): SearchHistoryEntity {
    return SearchHistoryEntity(
        id = this.id,
        memberId = this.memberId,
        query = this.query,
        answer = this.answer,
        referencedChunkIds = this.referencedChunkIds,
        feedback = this.feedback,
        createdAt = this.createdAt
    )
}