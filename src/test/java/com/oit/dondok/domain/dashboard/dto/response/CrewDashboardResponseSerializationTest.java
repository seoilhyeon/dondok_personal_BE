package com.oit.dondok.domain.dashboard.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oit.dondok.domain.crew.entity.CrewStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 크루 상세 대시보드 응답 JSON 계약(snake_case 필드명, enum 값, null 보존)을 고정한다.
class CrewDashboardResponseSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  @DisplayName("CrewDashboardResponse는 계약 필드명(snake_case)/enum 값으로 직렬화된다")
  void serializesWithSnakeCaseContractFieldNames() throws Exception {
    CrewDashboardResponse response =
        new CrewDashboardResponse(
            42L,
            "아침 갓생 30일",
            101L,
            null,
            CrewStatus.ACTIVE,
            "NONE",
            ProjectionStatus.LIVE,
            ProjectionNotice.ESTIMATED_NOT_FINAL,
            2,
            100_000L,
            5,
            103_226L,
            3_226L,
            2,
            5,
            1,
            OffsetDateTime.parse("2026-05-21T12:00:00+09:00"),
            List.of(new CrewDashboardParticipantResponse(101L, "갓생러", "0.235", true)),
            OffsetDateTime.parse("2026-05-21T12:00:00+09:00"));

    JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(root.has("crew_id")).isTrue();
    assertThat(root.has("crew_participant_id")).isTrue();
    assertThat(root.get("crew_status").asText()).isEqualTo("ACTIVE");
    assertThat(root.get("settlement_status").asText()).isEqualTo("NONE");
    assertThat(root.get("projection_status").asText()).isEqualTo("LIVE");
    assertThat(root.get("projection_notice").asText()).isEqualTo("ESTIMATED_NOT_FINAL");
    assertThat(root.has("days_until_end")).isTrue();
    assertThat(root.has("my_deposit_amount")).isTrue();
    assertThat(root.has("my_success_count")).isTrue();
    assertThat(root.has("my_expected_refund_amount")).isTrue();
    assertThat(root.has("my_expected_refund_delta_amount")).isTrue();
    assertThat(root.has("rank_total")).isTrue();
    assertThat(root.has("rank_delta")).isTrue();
    assertThat(root.has("next_settlement_at")).isTrue();
    assertThat(root.has("updated_at")).isTrue();
    // camelCase 키가 새지 않는지
    assertThat(root.has("crewId")).isFalse();
    assertThat(root.has("myExpectedRefundAmount")).isFalse();

    JsonNode participant = root.get("participants").get(0);
    assertThat(participant.has("crew_participant_id")).isTrue();
    assertThat(participant.has("share_ratio")).isTrue();
    assertThat(participant.get("is_me").asBoolean()).isTrue();
    // participants에는 개인별 success_count를 노출하지 않는다 (순위는 share_ratio로 산출)
    assertThat(participant.has("success_count")).isFalse();
  }

  @Test
  @DisplayName("적용 불가 필드는 생략하지 않고 null로 직렬화한다")
  void includesNullFieldsInsteadOfOmittingThem() throws Exception {
    CrewDashboardResponse response =
        new CrewDashboardResponse(
            42L,
            "모집 중 크루",
            101L,
            null,
            CrewStatus.RECRUITING,
            "NONE",
            ProjectionStatus.NOT_STARTED,
            ProjectionNotice.NOT_STARTED,
            null,
            100_000L,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            OffsetDateTime.parse("2026-05-21T12:00:00+09:00"));

    JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(root.has("settlement_id")).isTrue();
    assertThat(root.get("settlement_id").isNull()).isTrue();
    assertThat(root.has("my_expected_refund_amount")).isTrue();
    assertThat(root.get("my_expected_refund_amount").isNull()).isTrue();
    assertThat(root.has("rank")).isTrue();
    assertThat(root.get("rank").isNull()).isTrue();
    assertThat(root.has("next_settlement_at")).isTrue();
    assertThat(root.get("next_settlement_at").isNull()).isTrue();
  }
}
