package com.oit.dondok;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

@IntegrationTest
class DondokInfrastructureIntegrationTest {

  @Autowired DataSource dataSource;

  @Autowired StringRedisTemplate redisTemplate;

  @Test
  void connectsToMysqlAndRedisContainers() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getURL()).contains("jdbc:mysql://");
      assertThat(connection.createStatement().executeQuery("select 1").next()).isTrue();
    }

    redisTemplate.opsForValue().set("integration:test", "ok");

    assertThat(redisTemplate.opsForValue().get("integration:test")).isEqualTo("ok");
  }
}
