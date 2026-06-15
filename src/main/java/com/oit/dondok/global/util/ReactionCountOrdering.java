package com.oit.dondok.global.util;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// reaction_counts 정렬 규칙 단일 출처.
// 1) count 내림차순, 2) 최초 등장 시각(createdAt) 오름차순.
// 같은 횟수면 먼저 등장한 이모지가 왼쪽, 최근에 등장한 이모지가 오른쪽에 온다.
// 피드/미션로그/크루공지 리액션 집계가 공유한다. createdAt은 영속 엔티티에선 항상 존재하지만
// 미영속 엔티티(테스트 등) 방어로 nullsLast를 둔다.
public final class ReactionCountOrdering {

  private ReactionCountOrdering() {}

  public static <T> Map<String, Long> orderByCountThenCreatedAt(
      Collection<T> reactions,
      Function<T, String> reactionTypeExtractor,
      Function<T, LocalDateTime> createdAtExtractor) {
    Map<String, Long> counts = new LinkedHashMap<>();
    Map<String, LocalDateTime> firstSeen = new HashMap<>();
    for (T reaction : reactions) {
      String type = reactionTypeExtractor.apply(reaction);
      counts.merge(type, 1L, Long::sum);
      firstSeen.compute(
          type, (key, current) -> earliest(current, createdAtExtractor.apply(reaction)));
    }
    return counts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(
                    entry -> firstSeen.get(entry.getKey()),
                    Comparator.nullsLast(Comparator.naturalOrder())))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private static LocalDateTime earliest(LocalDateTime current, LocalDateTime candidate) {
    if (current == null) {
      return candidate;
    }
    if (candidate == null) {
      return current;
    }
    return current.isBefore(candidate) ? current : candidate;
  }
}
