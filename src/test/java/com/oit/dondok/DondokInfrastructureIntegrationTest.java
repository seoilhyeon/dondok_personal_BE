package com.oit.dondok;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.UUID;
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

    String key = "integration:test" + UUID.randomUUID();
    redisTemplate.opsForValue().set(key, "ok");
    assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("ok");
    redisTemplate.delete(key);
  }
}
