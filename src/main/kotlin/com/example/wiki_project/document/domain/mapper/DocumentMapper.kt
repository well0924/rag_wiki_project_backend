package com.example.wiki_project.document.domain.mapper

import com.example.wiki_project.document.domain.entity.DocumentEntity
import com.example.wiki_project.document.domain.model.DocumentModel

fun DocumentEntity.toModel(): DocumentModel = DocumentModel(
    id = this.id,
    title = this.title,
    contentUrl = this.contentUrl,
    docType = this.docType,
    fileSize = this.fileSize,
    status = this.status,
    createdBy = this.createdBy,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt
)

fun DocumentModel.toEntity(): DocumentEntity = DocumentEntity(
    id = this.id,
    title = this.title,
    contentUrl = this.contentUrl,
    docType = this.docType,
    fileSize = this.fileSize,
    status = this.status,
    createdBy = this.createdBy,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt
)