package com.oit.dondok;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@Tag("flyway")
@TestPropertySource(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
public class FlywayMigrationValidationTest {

  @Test
  void flywayMigrationMatchesEntitySchema() {
    // 빈 바디: context 로딩 성공 자체가 검증:
    // 1) Flyway: V1__init_shcema.sql 실행 성공
    // 2) Hibernate validate: entity 필드와 실제 MySQL 컬럼 일치 확인
  }
}
