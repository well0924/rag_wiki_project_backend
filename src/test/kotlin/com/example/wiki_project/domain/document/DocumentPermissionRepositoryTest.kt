package com.example.wiki_project.domain.document

import com.example.wiki_project.document.domain.entity.DocumentPermissionJpaEntity
import com.example.wiki_project.document.infrastructure.DocumentPermissionRepository
import com.example.wiki_project.member.domain.entity.UserGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentPermissionRepositoryTest @Autowired constructor(
    private val documentPermissionRepository: DocumentPermissionRepository,
    private val entityManager: TestEntityManager
) {

    @Test
    @DisplayName("출처 문서의 공개 그룹 이름은 요청자가 속한 그룹만 돌려준다")
    fun findGroupNamesForDocumentsReturnsOnlyMyGroups() {
        // given
        val management = UserGroup(groupName = "경영지원팀")
        val platform = UserGroup(groupName = "플랫폼개발팀")
        val secret = UserGroup(groupName = "비공개TF") // 요청자가 속하지 않은 그룹
        listOf(management, platform, secret).forEach { entityManager.persist(it) }

        val leaveDocId = 100L  // 경영지원팀 + 비공개TF에 공개
        val deployDocId = 200L // 플랫폼개발팀에만 공개
        listOf(
            DocumentPermissionJpaEntity(documentId = leaveDocId, groupId = management.id!!),
            DocumentPermissionJpaEntity(documentId = leaveDocId, groupId = secret.id!!),
            DocumentPermissionJpaEntity(documentId = deployDocId, groupId = platform.id!!)
        ).forEach { entityManager.persist(it) }
        entityManager.flush()
        entityManager.clear()

        // when: 요청자는 경영지원팀, 플랫폼개발팀 소속
        val result = documentPermissionRepository.findGroupNamesForDocuments(
            documentIds = listOf(leaveDocId, deployDocId),
            groupIds = listOf(management.id!!, platform.id!!)
        )

        // then: 비공개TF 이름은 내려가지 않음
        assertThat(result.map { it.documentId to it.groupName }).containsExactlyInAnyOrder(
            leaveDocId to "경영지원팀",
            deployDocId to "플랫폼개발팀"
        )
    }
}
