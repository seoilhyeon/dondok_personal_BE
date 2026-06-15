package com.oit.dondok.domain.crew.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.IntegrationTest;
import com.oit.dondok.domain.crew.dto.request.AddReactionRequest;
import com.oit.dondok.domain.crew.dto.response.ReactionResponse;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewNotice;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.crew.entity.HostPolicyVersion;
import com.oit.dondok.domain.member.entity.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 크루 공지 리액션 add의 read-after-write 회귀 테스트.
// 런타임과 동일하게 서비스가 자체 트랜잭션에서 돌도록 테스트 메서드는 비트랜잭션으로 둔다(부모 데이터는 별도 tx로 커밋).
// addReaction은 upsert 직후 같은 호출 안에서 집계를 다시 읽어 응답으로 돌려준다.
// 응답에 방금 추가한 리액션이 비어 있으면(=한 박자 밀림) 여기서 실패한다.
@IntegrationTest
@Tag("flyway")
@TestPropertySource(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class CrewNoticeReactionReadAfterWriteTest {

  @Autowired private CrewNoticeService crewNoticeService;
  @Autowired private PlatformTransactionManager transactionManager;
  @PersistenceContext private EntityManager entityManager;

  private UUID memberUuid;
  private Long crewId;
  private Long noticeId;

  @BeforeEach
  void setUp() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              Member member = Member.create("noticereactor@example.com", "password-hash", "공지리액터");
              entityManager.persist(member);
              Crew crew = createCrew(member);
              entityManager.persist(crew);
              entityManager.persist(
                  CrewParticipant.create(
                      crew, member, 10_000L, LocalDateTime.of(2026, 5, 31, 9, 0)));
              CrewNotice notice = CrewNotice.create(crew, member, "공지", "공지 내용");
              entityManager.persist(notice);
              entityManager.flush();
              this.memberUuid = member.getUuid();
              this.crewId = crew.getId();
              this.noticeId = notice.getId();
            });
  }

  @Test
  void responseReflectsJustAddedReaction() {
    ReactionResponse first =
        crewNoticeService.addReaction(crewId, noticeId, memberUuid, new AddReactionRequest("👏"));
    assertThat(first.myReactions()).containsExactly("👏");
    assertThat(first.reactionCounts()).containsEntry("👏", 1L);

    ReactionResponse second =
        crewNoticeService.addReaction(crewId, noticeId, memberUuid, new AddReactionRequest("🔥"));
    assertThat(second.myReactions()).containsExactlyInAnyOrder("👏", "🔥");
    assertThat(second.reactionCounts()).containsEntry("👏", 1L).containsEntry("🔥", 1L);
  }

  private Crew createCrew(Member host) {
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 9, 0);
    return Crew.create(
        host,
        "크루",
        "크루 설명",
        null,
        "OTHER",
        "{}",
        HostPolicyVersion.HOST_POLICY_V1,
        now,
        10_000L,
        2,
        5,
        now.plusDays(3),
        now.plusDays(4),
        now.plusDays(30));
  }
}
