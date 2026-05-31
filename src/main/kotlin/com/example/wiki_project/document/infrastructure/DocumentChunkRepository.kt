package com.example.wiki_project.document.infrastructure

import com.example.wiki_project.document.domain.entity.DocumentChunkEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DocumentChunkRepository: JpaRepository<DocumentChunkEntity, Long> {

}