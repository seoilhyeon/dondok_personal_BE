package com.oit.dondok.global.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.oit.dondok.config.JpaAuditingConfig;
import com.oit.dondok.domain.auth.entity.MemberRefreshToken;
import com.oit.dondok.domain.crew.entity.Crew;
import com.oit.dondok.domain.crew.entity.CrewNotice;
import com.oit.dondok.domain.crew.entity.CrewNoticeComment;
import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.domain.crew.entity.CrewParticipant;
import com.oit.dondok.domain.member.entity.Member;
import com.oit.dondok.domain.member.entity.MemberStatus;
import com.oit.dondok.domain.mission.entity.MissionLog;
import com.oit.dondok.domain.mission.entity.MissionLogReaction;
import com.oit.dondok.domain.mission.entity.MissionRule;
import com.oit.dondok.domain.mission.entity.MissionScheduleDay;
import com.oit.dondok.domain.notification.entity.Notification;
import com.oit.dondok.domain.notification.entity.NotificationDevice;
import com.oit.dondok.domain.point.entity.PointAccount;
import com.oit.dondok.domain.point.entity.PointHistory;
import com.oit.dondok.domain.settlement.entity.Settlement;
import com.oit.dondok.domain.settlement.entity.SettlementItem;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AuditingEntityTest {

  private static final List<Class<?>> APPEND_ONLY_ENTITIES =
      List.of(PointHistory.class, MemberRefreshToken.class, MissionScheduleDay.class);

  private static final List<Class<?>> MUTABLE_ENTITIES =
      List.of(
          Member.class,
          Crew.class,
          CrewNotice.class,
          CrewNoticeComment.class,
          CrewNoticeReaction.class,
          CrewParticipant.class,
          MissionLog.class,
          MissionLogReaction.class,
          MissionRule.class,
          Notification.class,
          NotificationDevice.class,
          PointAccount.class,
          Settlement.class,
          SettlementItem.class);

  @Autowired private EntityManager entityManager;

  @Test
  void appendOnlyEntitiesShouldUseCreatedTimeEntityOnly() {
    for (Class<?> entityClass : APPEND_ONLY_ENTITIES) {
      assertThat(entityClass).isAssignableTo(CreatedTimeEntity.class);
      assertThat(AuditableTimeEntity.class.isAssignableFrom(entityClass)).isFalse();
      assertThat(hasNoArgumentMethod(entityClass, "getUpdatedAt")).isFalse();
    }
  }

  @Test
  void mutableEntitiesShouldUseAuditableTimeEntity() {
    for (Class<?> entityClass : MUTABLE_ENTITIES) {
      assertThat(entityClass).isAssignableTo(AuditableTimeEntity.class);
    }
  }

  @Test
  void appendOnlyEntitiesShouldNotExposeUpdatedAtFieldPropertyOrColumnMapping() {
    Metamodel metamodel = entityManager.getMetamodel();

    for (Class<?> entityClass : APPEND_ONLY_ENTITIES) {
      EntityType<?> entityType = metamodel.entity(entityClass);

      assertThat(hasFieldInHierarchy(entityClass, "updatedAt")).isFalse();
      assertThat(hasNoArgumentMethod(entityClass, "getUpdatedAt")).isFalse();
      assertThat(entityType.getAttributes())
          .noneMatch(attribute -> "updatedAt".equals(attribute.getName()));
      assertThat(hasColumnMappingInHierarchy(entityClass, "updated_at")).isFalse();
    }
  }

  @Test
  void jpaAuditingShouldPopulateAndUpdateMemberAuditTimes() throws Exception {
    Member member = newMember("before@example.com", "before");

    entityManager.persist(member);
    entityManager.flush();

    entityManager.clear();

    Member persistedMember = entityManager.find(Member.class, member.getId());
    LocalDateTime persistedCreatedAt = persistedMember.getCreatedAt();
    LocalDateTime persistedUpdatedAt = persistedMember.getUpdatedAt();

    assertThat(persistedCreatedAt).isNotNull();
    assertThat(persistedUpdatedAt).isNotNull();

    LocalDateTime staleUpdatedAt = persistedUpdatedAt.minusDays(1L);
    ReflectionTestUtils.setField(persistedMember, "updatedAt", staleUpdatedAt);
    ReflectionTestUtils.setField(persistedMember, "nickname", "after");
    entityManager.flush();
    entityManager.clear();

    Member updatedMember = entityManager.find(Member.class, member.getId());

    assertThat(updatedMember.getCreatedAt()).isEqualTo(persistedCreatedAt);
    assertThat(updatedMember.getUpdatedAt()).isAfter(staleUpdatedAt);
  }

  private static Member newMember(String email, String nickname) throws Exception {
    Member member = newInstance(Member.class);
    ReflectionTestUtils.setField(member, "uuid", UUID.randomUUID());
    ReflectionTestUtils.setField(member, "email", email);
    ReflectionTestUtils.setField(member, "nickname", nickname);
    ReflectionTestUtils.setField(member, "status", MemberStatus.ACTIVE);
    return member;
  }

  private static <T> T newInstance(Class<T> entityClass) throws Exception {
    Constructor<T> constructor = entityClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }

  private static boolean hasFieldInHierarchy(Class<?> entityClass, String fieldName) {
    Class<?> currentClass = entityClass;
    while (currentClass != null) {
      for (Field field : currentClass.getDeclaredFields()) {
        if (field.getName().equals(fieldName)) {
          return true;
        }
      }
      currentClass = currentClass.getSuperclass();
    }
    return false;
  }

  private static boolean hasNoArgumentMethod(Class<?> entityClass, String methodName) {
    for (Method method : entityClass.getMethods()) {
      if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasColumnMappingInHierarchy(Class<?> entityClass, String columnName) {
    Class<?> currentClass = entityClass;
    while (currentClass != null) {
      for (Field field : currentClass.getDeclaredFields()) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && column.name().equals(columnName)) {
          return true;
        }
      }
      currentClass = currentClass.getSuperclass();
    }
    return false;
  }
}
