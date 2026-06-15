package com.oit.dondok.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReactionCountOrderingTest {

  private record Reaction(String type, LocalDateTime createdAt) {}

  private static Map<String, Long> order(List<Reaction> reactions) {
    return ReactionCountOrdering.orderByCountThenCreatedAt(
        reactions, Reaction::type, Reaction::createdAt);
  }

  @Test
  void ordersByCountDescending() {
    LocalDateTime base = LocalDateTime.of(2026, 6, 15, 0, 0);
    List<Reaction> reactions =
        List.of(
            new Reaction("👍", base),
            new Reaction("❤", base.plusSeconds(1)),
            new Reaction("❤", base.plusSeconds(2)),
            new Reaction("😂", base.plusSeconds(3)),
            new Reaction("😂", base.plusSeconds(4)),
            new Reaction("😂", base.plusSeconds(5)));

    Map<String, Long> result = order(reactions);

    assertThat(result)
        .containsExactly(Map.entry("😂", 3L), Map.entry("❤", 2L), Map.entry("👍", 1L));
  }

  @Test
  void breaksCountTieByEarliestCreatedAt() {
    LocalDateTime base = LocalDateTime.of(2026, 6, 15, 0, 0);
    // 모두 count=1. 먼저 등장한 이모지가 왼쪽에 온다.
    List<Reaction> reactions =
        List.of(
            new Reaction("😂", base.plusSeconds(2)),
            new Reaction("👍", base.plusSeconds(1)),
            new Reaction("❤", base.plusSeconds(3)));

    Map<String, Long> result = order(reactions);

    assertThat(result.keySet()).containsExactly("👍", "😂", "❤");
  }

  @Test
  void usesEarliestOccurrenceWhenSameTypeAppearsMultipleTimes() {
    LocalDateTime base = LocalDateTime.of(2026, 6, 15, 0, 0);
    // ❤의 최초 등장(base)이 👍의 최초 등장(base+1)보다 빠르므로 동률에서 ❤가 앞선다.
    List<Reaction> reactions =
        List.of(
            new Reaction("❤", base.plusSeconds(5)),
            new Reaction("❤", base),
            new Reaction("👍", base.plusSeconds(1)),
            new Reaction("👍", base.plusSeconds(2)));

    Map<String, Long> result = order(reactions);

    assertThat(result.keySet()).containsExactly("❤", "👍");
  }

  @Test
  void placesNullCreatedAtLastAmongTies() {
    LocalDateTime base = LocalDateTime.of(2026, 6, 15, 0, 0);
    // 미영속 엔티티 등 createdAt이 null인 타입은 동률에서 뒤로 밀린다(nullsLast).
    List<Reaction> reactions = List.of(new Reaction("😂", null), new Reaction("👍", base));

    Map<String, Long> result = order(reactions);

    assertThat(result.keySet()).containsExactly("👍", "😂");
  }

  @Test
  void returnsEmptyMapForEmptyInput() {
    assertThat(order(List.of())).isEmpty();
  }
}
