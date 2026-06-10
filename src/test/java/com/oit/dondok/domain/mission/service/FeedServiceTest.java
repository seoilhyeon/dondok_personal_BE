package com.oit.dondok.domain.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.oit.dondok.domain.crew.exception.CrewErrorCode;
import com.oit.dondok.domain.image.port.ImageDeliveryPort;
import com.oit.dondok.domain.image.port.ImageDeliveryUrl;
import com.oit.dondok.domain.image.port.ImageObjectKey;
import com.oit.dondok.domain.mission.dto.response.AvailableCrewResponse;
import com.oit.dondok.domain.mission.dto.response.FeedItemResponse;
import com.oit.dondok.domain.mission.dto.response.FeedResponse;
import com.oit.dondok.domain.mission.entity.CertificationStatus;
import com.oit.dondok.domain.mission.repository.FeedItemRow;
import com.oit.dondok.domain.mission.repository.FeedQueryRepository;
import com.oit.dondok.domain.mission.repository.ReactionRow;
import com.oit.dondok.global.exception.CustomException;
import com.oit.dondok.global.exception.GlobalErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

  private static final UUID ME = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c901");
  private static final UUID OTHER = UUID.fromString("018f4fd2-6d7a-7a41-9f58-6d07f5c3c902");
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final AvailableCrewResponse CREW_A = new AvailableCrewResponse(42L, "갓생 6시 기상");
  private static final AvailableCrewResponse CREW_B = new AvailableCrewResponse(43L, "독서 1챕터");
  private static final LocalDateTime T = LocalDateTime.of(2026, 6, 9, 6, 5, 10);

  @Mock private FeedQueryRepository feedQueryRepository;
  @Mock private ImageDeliveryPort imageDeliveryPort;

  @InjectMocks private FeedService feedService;

  // crew_id 미지정: 스코프는 내 전체 참여 크루, available_crews도 전체.
  @Test
  void usesAllParticipatingCrewsWhenCrewIdNull() {
    givenMyCrews(CREW_A, CREW_B);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());

    FeedResponse res = feedService.getFeed(ME, null, null, null, null, null);

    assertThat(res.availableCrews()).containsExactly(CREW_A, CREW_B);
    ArgumentCaptor<Collection<Long>> crewIds = captor();
    verify(feedQueryRepository)
        .findFeedItems(crewIds.capture(), any(), any(), any(), any(), anyInt());
    assertThat(crewIds.getValue()).containsExactlyInAnyOrder(42L, 43L);
  }

  // crew_id가 내 참여 크루면 그 크루로만 스코프를 좁힌다.
  @Test
  void scopesToSingleCrewWhenParticipating() {
    givenMyCrews(CREW_A, CREW_B);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());

    feedService.getFeed(ME, 42L, null, null, null, null);

    ArgumentCaptor<Collection<Long>> crewIds = captor();
    verify(feedQueryRepository)
        .findFeedItems(crewIds.capture(), any(), any(), any(), any(), anyInt());
    assertThat(crewIds.getValue()).containsExactly(42L);
  }

  // crew_id가 내 참여 크루가 아니면 CREW_ACCESS_DENIED, 조회는 수행하지 않는다.
  @Test
  void throwsAccessDeniedWhenCrewIdNotParticipating() {
    givenMyCrews(CREW_A);

    assertThatThrownBy(() -> feedService.getFeed(ME, 99L, null, null, null, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(CrewErrorCode.CREW_ACCESS_DENIED);

    verify(feedQueryRepository, never()).findFeedItems(any(), any(), any(), any(), any(), anyInt());
  }

  // 빈 페이지: feed_items 비고 next_cursor null, 리액션 조회도 하지 않는다.
  @Test
  void emptyPageReturnsEmptyItemsAndNullCursor() {
    givenMyCrews(CREW_A);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());

    FeedResponse res = feedService.getFeed(ME, null, null, null, null, null);

    assertThat(res.feedItems()).isEmpty();
    assertThat(res.nextCursor()).isNull();
    assertThat(res.availableCrews()).containsExactly(CREW_A);
    verify(feedQueryRepository, never()).findReactionRows(any());
  }

  // hasNext: limit+1개가 오면 limit개만 반환하고 마지막 항목으로 next_cursor를 만든다.
  @Test
  void buildsNextCursorWhenHasNext() {
    givenMyCrews(CREW_A);
    givenImageDelivery();
    FeedItemRow r1 = row(9003L, T.plusMinutes(2));
    FeedItemRow r2 = row(9002L, T.plusMinutes(1));
    FeedItemRow extra = row(9001L, T); // limit=2 초과분
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), eq(2)))
        .willReturn(List.of(r1, r2, extra));
    given(feedQueryRepository.findReactionRows(any())).willReturn(List.of());

    FeedResponse res = feedService.getFeed(ME, null, null, null, null, 2);

    assertThat(res.feedItems()).hasSize(2);
    assertThat(res.feedItems())
        .extracting(FeedItemResponse::missionLogId)
        .containsExactly(9003L, 9002L);
    // next_cursor = {반환된 마지막 항목 server_time(+09:00)}_{id}
    assertThat(res.nextCursor())
        .isEqualTo(T.plusMinutes(1).atZone(SEOUL).toOffsetDateTime() + "_9002");
  }

  // 페이지가 limit 이하면 next_cursor는 null이다.
  @Test
  void noNextCursorWhenPageNotFull() {
    givenMyCrews(CREW_A);
    givenImageDelivery();
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), eq(2)))
        .willReturn(List.of(row(9001L, T)));
    given(feedQueryRepository.findReactionRows(any())).willReturn(List.of());

    FeedResponse res = feedService.getFeed(ME, null, null, null, null, 2);

    assertThat(res.feedItems()).hasSize(1);
    assertThat(res.nextCursor()).isNull();
  }

  // 리액션 매핑: counts는 (token별 합계), my_reactions는 호출자가 단 token만.
  @Test
  void mapsReactionCountsAndMyReactions() {
    givenMyCrews(CREW_A);
    givenImageDelivery();
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of(row(9001L, T)));
    given(feedQueryRepository.findReactionRows(any()))
        .willReturn(
            List.of(
                new ReactionRow(9001L, "clap", ME),
                new ReactionRow(9001L, "clap", OTHER),
                new ReactionRow(9001L, "fire", OTHER)));

    FeedItemResponse item =
        feedService.getFeed(ME, null, null, null, null, null).feedItems().get(0);

    assertThat(item.reactionCounts()).containsOnly(Map.entry("clap", 2L), Map.entry("fire", 1L));
    assertThat(item.myReactions()).containsExactly("clap");
  }

  // 리액션이 없는 항목은 빈 map / 빈 list.
  @Test
  void itemWithoutReactionsHasEmptyMaps() {
    givenMyCrews(CREW_A);
    givenImageDelivery();
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of(row(9001L, T)));
    given(feedQueryRepository.findReactionRows(any())).willReturn(List.of());

    FeedItemResponse item =
        feedService.getFeed(ME, null, null, null, null, null).feedItems().get(0);

    assertThat(item.reactionCounts()).isEmpty();
    assertThat(item.myReactions()).isEmpty();
  }

  // 이미지/프로필 URL은 ImageDeliveryPort로 파생한다.
  @Test
  void derivesImageAndProfileUrls() {
    givenMyCrews(CREW_A);
    givenImageDelivery();
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of(row(9001L, T)));
    given(feedQueryRepository.findReactionRows(any())).willReturn(List.of());

    FeedItemResponse item =
        feedService.getFeed(ME, null, null, null, null, null).feedItems().get(0);

    assertThat(item.imageUrl()).isEqualTo("https://cdn/mission/9001");
    assertThat(item.profileImageUrl()).isEqualTo("https://cdn/profile/9001");
  }

  // 프로필 S3 key가 null이면 profile_image_url은 null이고 delivery 호출도 이미지 1건뿐이다.
  @Test
  void nullProfileKeyYieldsNullProfileUrl() {
    givenMyCrews(CREW_A);
    givenImageDelivery();
    FeedItemRow noProfile =
        new FeedItemRow(
            9001L,
            42L,
            "갓생 6시 기상",
            101L,
            ME,
            "닉",
            null,
            "mission/9001",
            "캡션",
            T,
            CertificationStatus.SUCCESS);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of(noProfile));
    given(feedQueryRepository.findReactionRows(any())).willReturn(List.of());

    FeedItemResponse item =
        feedService.getFeed(ME, null, null, null, null, null).feedItems().get(0);

    assertThat(item.profileImageUrl()).isNull();
    assertThat(item.imageUrl()).isEqualTo("https://cdn/mission/9001");
    verify(imageDeliveryPort, times(1)).createDeliveryUrl(any(), any());
  }

  // server_time은 Asia/Seoul offset의 OffsetDateTime으로 변환된다.
  @Test
  void convertsServerTimeToSeoulOffset() {
    givenMyCrews(CREW_A);
    givenImageDelivery();
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of(row(9001L, T)));
    given(feedQueryRepository.findReactionRows(any())).willReturn(List.of());

    FeedItemResponse item =
        feedService.getFeed(ME, null, null, null, null, null).feedItems().get(0);

    assertThat(item.serverTime()).isEqualTo(T.atZone(SEOUL).toOffsetDateTime());
    assertThat(item.serverTime().toString()).endsWith("+09:00");
  }

  // 단일 날짜(from만): [from 00:00, 다음날 00:00) 윈도우.
  @Test
  void fromOnlyUsesSingleDayWindow() {
    givenMyCrews(CREW_A);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());

    feedService.getFeed(ME, null, LocalDate.of(2026, 6, 9), null, null, null);

    ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
    ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(feedQueryRepository)
        .findFeedItems(any(), from.capture(), to.capture(), any(), any(), anyInt());
    assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 6, 9, 0, 0));
    assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 6, 10, 0, 0));
  }

  // 기간(from~to): [from 00:00, to+1일 00:00) 윈도우.
  @Test
  void fromToUsesRangeWindow() {
    givenMyCrews(CREW_A);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());

    feedService.getFeed(ME, null, LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 11), null, null);

    ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
    ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(feedQueryRepository)
        .findFeedItems(any(), from.capture(), to.capture(), any(), any(), anyInt());
    assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 6, 9, 0, 0));
    assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 6, 12, 0, 0));
  }

  // 날짜 미지정이면 범위 경계는 null.
  @Test
  void noDatesYieldNullBounds() {
    givenMyCrews(CREW_A);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());

    feedService.getFeed(ME, null, null, null, null, null);

    ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
    ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(feedQueryRepository)
        .findFeedItems(any(), from.capture(), to.capture(), any(), any(), anyInt());
    assertThat(from.getValue()).isNull();
    assertThat(to.getValue()).isNull();
  }

  // 유효 cursor는 server_time(LocalDateTime)·id로 파싱돼 조회에 전달된다.
  @Test
  void parsesValidCursor() {
    givenMyCrews(CREW_A);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());
    String cursor = T.atZone(SEOUL).toOffsetDateTime() + "_9001";

    feedService.getFeed(ME, null, null, null, cursor, null);

    ArgumentCaptor<LocalDateTime> cTime = ArgumentCaptor.forClass(LocalDateTime.class);
    ArgumentCaptor<Long> cId = ArgumentCaptor.forClass(Long.class);
    verify(feedQueryRepository)
        .findFeedItems(any(), any(), any(), cTime.capture(), cId.capture(), anyInt());
    assertThat(cTime.getValue()).isEqualTo(T);
    assertThat(cId.getValue()).isEqualTo(9001L);
  }

  // cursor 없으면 server_time/id 모두 null로 전달(첫 페이지).
  @Test
  void nullCursorPassesNullBounds() {
    givenMyCrews(CREW_A);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());

    feedService.getFeed(ME, null, null, null, null, null);

    ArgumentCaptor<LocalDateTime> cTime = ArgumentCaptor.forClass(LocalDateTime.class);
    ArgumentCaptor<Long> cId = ArgumentCaptor.forClass(Long.class);
    verify(feedQueryRepository)
        .findFeedItems(any(), any(), any(), cTime.capture(), cId.capture(), anyInt());
    assertThat(cTime.getValue()).isNull();
    assertThat(cId.getValue()).isNull();
  }

  // 잘못된 cursor(구분자 없음/날짜 깨짐/id 깨짐)는 INVALID_INPUT.
  @Test
  void malformedCursorThrowsInvalidInput() {
    givenMyCrews(CREW_A);

    assertInvalidCursor("no-separator");
    assertInvalidCursor("not-a-date_9001");
    assertInvalidCursor(T.atZone(SEOUL).toOffsetDateTime() + "_not-a-number");
  }

  // limit null이면 기본 20.
  @Test
  void defaultLimitWhenNull() {
    assertLimitPassed(null, 20);
  }

  // limit 0이면 1로 클램프.
  @Test
  void clampsZeroToOne() {
    assertLimitPassed(0, 1);
  }

  // limit 음수면 1로 클램프.
  @Test
  void clampsNegativeToOne() {
    assertLimitPassed(-5, 1);
  }

  // limit 100 초과면 100으로 클램프.
  @Test
  void clampsAboveMax() {
    assertLimitPassed(500, 100);
  }

  // 범위 내 limit은 그대로 사용.
  @Test
  void usesGivenLimit() {
    assertLimitPassed(50, 50);
  }

  // ---- helpers ----

  private void assertInvalidCursor(String cursor) {
    assertThatThrownBy(() -> feedService.getFeed(ME, null, null, null, cursor, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT);
  }

  private void assertLimitPassed(Integer given, int expected) {
    givenMyCrews(CREW_A);
    given(feedQueryRepository.findFeedItems(any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());

    feedService.getFeed(ME, null, null, null, null, given);

    verify(feedQueryRepository).findFeedItems(any(), any(), any(), any(), any(), eq(expected));
  }

  private void givenMyCrews(AvailableCrewResponse... crews) {
    given(feedQueryRepository.findParticipatingCrews(ME)).willReturn(List.of(crews));
  }

  private void givenImageDelivery() {
    given(imageDeliveryPort.createDeliveryUrl(any(ImageObjectKey.class), any()))
        .willAnswer(
            inv ->
                new ImageDeliveryUrl(
                    "https://cdn/" + ((ImageObjectKey) inv.getArgument(0)).value(),
                    OffsetDateTime.parse("2026-06-09T06:15:10+09:00")));
  }

  private FeedItemRow row(Long missionLogId, LocalDateTime serverTime) {
    return new FeedItemRow(
        missionLogId,
        42L,
        "갓생 6시 기상",
        101L,
        ME,
        "닉네임",
        "profile/" + missionLogId,
        "mission/" + missionLogId,
        "캡션",
        serverTime,
        CertificationStatus.SUCCESS);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Collection<Long>> captor() {
    return ArgumentCaptor.forClass(Collection.class);
  }
}
