package com.oit.dondok.domain.settlement.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementCalculationReasonTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void parseShouldKeepUnknownMetadataFields() throws Exception {
    String json =
        """
        {
          "participant_key": "1",
          "recognized_success_count": 5,
          "included_dates": ["2026-05-01", "2026-05-02"],
          "excluded_logs": [{"server_time":"2026-05-02T07:10:11+09:00","code":"DAILY_DUPLICATE"}],
          "remainder_policy": "HOST_REMAINDER"
        }
        """;

    SettlementCalculationReason reason = SettlementCalculationReason.parse(json);

    assertThat(reason.participantKey()).isEqualTo("1");
    assertThat(reason.recognizedSuccessCount()).isEqualTo(5);
    assertThat(reason.metadata())
        .containsKey("included_dates")
        .containsKey("excluded_logs")
        .containsAllEntriesOf(
            Map.of(
                "included_dates",
                OBJECT_MAPPER.readTree("[\"2026-05-01\", \"2026-05-02\"]"),
                "excluded_logs",
                OBJECT_MAPPER.readTree(
                    "[{\"server_time\":\"2026-05-02T07:10:11+09:00\",\"code\":\"DAILY_DUPLICATE\"}]")))
        .doesNotContainKey("participant_key")
        .doesNotContainKey("recognized_success_count");
  }

  @Test
  void toJsonShouldPreserveFreeformMetadata() throws Exception {
    SettlementCalculationReason reason =
        SettlementCalculationReason.parse(
            """
            {
              "participant_key": "1",
              "included_dates": ["2026-05-01", "2026-05-02"],
              "excluded_logs": [{"server_time":"2026-05-02T07:10:11+09:00","code":"DAILY_DUPLICATE"}]
            }
            """);

    JsonNode expected =
        OBJECT_MAPPER.readTree(
            """
            {
              "participant_key": "1",
              "included_dates": ["2026-05-01", "2026-05-02"],
              "excluded_logs": [{"server_time":"2026-05-02T07:10:11+09:00","code":"DAILY_DUPLICATE"}]
            }
            """);

    assertThat(OBJECT_MAPPER.readTree(reason.toJson())).isEqualTo(expected);
  }

  @Test
  void parseShouldRejectNonObjectPayload() {
    assertThatThrownBy(() -> SettlementCalculationReason.parse("[]"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
