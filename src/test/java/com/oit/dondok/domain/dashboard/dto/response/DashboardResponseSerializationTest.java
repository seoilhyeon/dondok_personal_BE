package com.oit.dondok.domain.dashboard.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 응답 JSON 필드명(snake_case) 계약을 고정한다. accessor만 보는 단위 테스트로는 잡히지 않는
// 필드명 오타/누락을 회귀 방지하기 위함이다.
class DashboardResponseSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("DashboardResponse는 계약 필드명(snake_case)으로 직렬화된다")
  void serializesWithSnakeCaseContractFieldNames() throws Exception {
    DashboardResponse response =
        new DashboardResponse(
            57_260L,
            960L,
            "0.017",
            3,
            1,
            new MaxDeltaCrewResponse(10L, "아침 6시 기상", 1_200L),
            List.of(
                new DashboardCrewResponse(
                    10L, "아침 6시 기상", "https://cdn.example.com/crew/img", "0.41", 23_500L, 1_200L)));

    JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(root.has("total_expected_refund_amount")).isTrue();
    assertThat(root.has("today_delta_amount")).isTrue();
    assertThat(root.has("today_delta_ratio")).isTrue();
    assertThat(root.has("rising_crew_count")).isTrue();
    assertThat(root.has("falling_crew_count")).isTrue();
    assertThat(root.has("max_delta_crew")).isTrue();
    assertThat(root.has("crews")).isTrue();
    // camelCase 키가 새지 않는지 확인
    assertThat(root.has("totalExpectedRefundAmount")).isFalse();

    JsonNode maxDeltaCrew = root.get("max_delta_crew");
    assertThat(maxDeltaCrew.has("crew_id")).isTrue();
    assertThat(maxDeltaCrew.has("crew_name")).isTrue();
    assertThat(maxDeltaCrew.has("today_delta_amount")).isTrue();

    JsonNode crew = root.get("crews").get(0);
    assertThat(crew.has("crew_id")).isTrue();
    assertThat(crew.has("crew_name")).isTrue();
    assertThat(crew.has("image_url")).isTrue();
    assertThat(crew.has("share_ratio")).isTrue();
    assertThat(crew.has("expected_refund_amount")).isTrue();
    assertThat(crew.has("today_delta_amount")).isTrue();
  }

  @Test
  @DisplayName("적용 불가 필드는 생략하지 않고 null로 직렬화한다")
  void includesNullFieldsInsteadOfOmittingThem() throws Exception {
    DashboardResponse response =
        new DashboardResponse(
            0L,
            0L,
            "0",
            0,
            0,
            null,
            List.of(new DashboardCrewResponse(13L, "모집 중 크루", null, null, null, null)));

    JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(response));

    // max_delta_crew는 키가 존재하며 값이 null
    assertThat(root.has("max_delta_crew")).isTrue();
    assertThat(root.get("max_delta_crew").isNull()).isTrue();

    JsonNode crew = root.get("crews").get(0);
    assertThat(crew.has("image_url")).isTrue();
    assertThat(crew.get("image_url").isNull()).isTrue();
    assertThat(crew.has("share_ratio")).isTrue();
    assertThat(crew.get("share_ratio").isNull()).isTrue();
    assertThat(crew.has("expected_refund_amount")).isTrue();
    assertThat(crew.get("expected_refund_amount").isNull()).isTrue();
    assertThat(crew.has("today_delta_amount")).isTrue();
    assertThat(crew.get("today_delta_amount").isNull()).isTrue();
  }
}
