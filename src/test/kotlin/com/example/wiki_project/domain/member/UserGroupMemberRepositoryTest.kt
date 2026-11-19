package com.example.wiki_project.domain.member

import com.example.wiki_project.member.domain.entity.MemberEntity
import com.example.wiki_project.member.domain.entity.UserGroup
import com.example.wiki_project.member.domain.entity.UserGroupMember
import com.example.wiki_project.member.domain.model.GroupRole
import com.example.wiki_project.member.infrastructure.UserGroupMemberRepository
import org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserGroupMemberRepositoryTest @Autowired constructor(
    private val userGroupMemberRepository: UserGroupMemberRepository,
    private val entityManager: TestEntityManager
) {

    @Test
    @DisplayName("복합키(IdClass) 구조의 매핑 엔티티가 정상적으로 저장되고 복합키로 조회되어야 한다")
    fun saveAndFindByCompositeKeySuccess() {
        // given
        // 1. 선행 데이터 구축 (Member와 Group이 영속화되어 있어야 매핑 테이블에 넣을 수 있음)
        val member = MemberEntity(email = "test@company.com", password = "password123", userName = "유저A")
        val group = UserGroup(groupName = "인프라개발팀", description = "코어 인프라 관리")

        entityManager.persist(member)
        entityManager.persist(group)
        entityManager.flush()

        // 2. 매핑 엔티티 생성
        val userGroupMember = UserGroupMember(member = member, group = group)

        // when
        userGroupMemberRepository.save(userGroupMember)
        entityManager.flush()
        entityManager.clear() // 영속성 컨텍스트를 비워서 실제 DB 조회를 강제함

        // then
        // 복합키 식별자 클래스를 이용해 직접 조회 검증
        val mappings = userGroupMemberRepository.findByMemberId(member.id!!)

        assertThat(mappings).isNotEmpty
        val foundMapping = mappings.first()

        assertThat(foundMapping.member.id).isEqualTo(member.id)
        assertThat(foundMapping.group.id).isEqualTo(group.id)
    }

    @Test
    @DisplayName("findByMemberId 호출 시 선두 복합키 인덱스를 타고 해당 회원이 속한 모든 그룹 매핑 데이터를 정상 조회한다")
    fun findByMemberIdSuccess() {
        // given
        val member = MemberEntity(email = "user@company.com", password = "password", userName = "유저B")
        val group1 = UserGroup(groupName = "보안팀", description = "전사 보안")
        val group2 = UserGroup(groupName = "인사팀", description = "HR")

        entityManager.persist(member)
        entityManager.persist(group1)
        entityManager.persist(group2)

        // 한 명의 유저를 두 개의 그룹에 매핑
        val mapping1 = UserGroupMember(member = member, group = group1)
        val mapping2 = UserGroupMember(member = member, group = group2)

        userGroupMemberRepository.save(mapping1)
        userGroupMemberRepository.save(mapping2)

        entityManager.flush()
        entityManager.clear()

        // when
        // 네가 아까 서비스 레이어 진영에서 호출했던 그 메서드 검증
        val mappings = userGroupMemberRepository.findByMemberId(member.id!!)

        // then
        assertThat(mappings).hasSize(2)

        // 정렬 성능 최적화와 별개로 데이터 정합성이 일치하는지 검증
        val groupIds = mappings.map { it.group.id!! }
        assertThat(groupIds).containsExactlyInAnyOrder(group1.id, group2.id)
    }

    @Test
    @DisplayName("그룹 이름 중복 검사는 OWNER 기준이다: 다른 사람이 만든 같은 이름과 MEMBER로 속한 그룹은 세지 않고, 제외할 그룹은 뺀다")
    fun countGroupsByMemberRoleAndNameCountsOnlyOwnedGroups() {
        // given
        val owner = MemberEntity(email = "owner@company.com", password = "password", userName = "OWNER")
        val other = MemberEntity(email = "other@company.com", password = "password", userName = "다른사람")
        val myStudy = UserGroup(groupName = "스프링 스터디")
        val othersStudy = UserGroup(groupName = "스프링 스터디") // 다른 사람이 만든 같은 이름 (전체 유니크 제약이 없어야 저장됨)
        listOf(owner, other, myStudy, othersStudy).forEach { entityManager.persist(it) }

        userGroupMemberRepository.save(UserGroupMember(member = owner, group = myStudy, role = GroupRole.OWNER))
        userGroupMemberRepository.save(UserGroupMember(member = other, group = othersStudy, role = GroupRole.OWNER))
        // owner가 다른 사람의 그룹에 MEMBER로도 속해 있음 → OWNER 기준 검사에서는 세지 않아야 함
        userGroupMemberRepository.save(UserGroupMember(member = owner, group = othersStudy, role = GroupRole.MEMBER))
        entityManager.flush()
        entityManager.clear()

        // when & then
        // 1. 생성 시(제외 그룹 없음): 내가 OWNER인 "스프링 스터디"는 1개
        assertThat(userGroupMemberRepository.countGroupsByMemberRoleAndName(owner.id!!, GroupRole.OWNER, "스프링 스터디", 0L))
            .isEqualTo(1L)
        // 2. 수정 시 자기 자신을 제외하면 0개
        assertThat(userGroupMemberRepository.countGroupsByMemberRoleAndName(owner.id!!, GroupRole.OWNER, "스프링 스터디", myStudy.id!!))
            .isEqualTo(0L)
        // 3. 내가 OWNER가 아닌 이름은 0개
        assertThat(userGroupMemberRepository.countGroupsByMemberRoleAndName(owner.id!!, GroupRole.OWNER, "없는 그룹", 0L))
            .isEqualTo(0L)
    }
}