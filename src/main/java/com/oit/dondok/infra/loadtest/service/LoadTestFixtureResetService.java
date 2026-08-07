package com.oit.dondok.infra.loadtest.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

/** Removes only data created by the load-test fixture namespace. */
@Service
@Profile("load-test & !prod")
@RequiredArgsConstructor
public class LoadTestFixtureResetService {
  private static final String FIXTURE_EMAIL_PATTERN = "load-test%@local.invalid";
  private static final String FIXTURE_CREW_TITLE_PATTERN = "load-test-settlement-%";
  private static final String REDIS_PREFIX = "load-test:*";
  private static final String S3_PREFIX = "load-test/";

  private final JdbcTemplate jdbcTemplate;
  private final StringRedisTemplate redisTemplate;
  private final S3Client s3Client;
  private final TransactionTemplate transactionTemplate;

  @Value("${app.aws.s3.bucket}")
  private String bucket;

  public void reset() {
    transactionTemplate.executeWithoutResult(status -> deleteFixtureDatabaseRows());
    deleteFixtureRedisKeys();
    deleteFixtureS3Objects();
  }

  private void deleteFixtureDatabaseRows() {
    delete(
        "delete from notification where member_id in (select id from member where email like ?)",
        FIXTURE_EMAIL_PATTERN);
    delete(
        "delete from settlement_item where member_id in (select id from member where email like ?)",
        FIXTURE_EMAIL_PATTERN);
    delete(
        "delete dsps from daily_settlement_participant_snapshot dsps "
            + "join daily_settlement_snapshot dss on dss.id = dsps.daily_settlement_snapshot_id "
            + "join crew c on c.id = dss.crew_id where c.title like ?",
        FIXTURE_CREW_TITLE_PATTERN);
    delete(
        "delete dss from daily_settlement_snapshot dss join crew c on c.id = dss.crew_id "
            + "where c.title like ?",
        FIXTURE_CREW_TITLE_PATTERN);
    delete(
        "delete s from settlement s join crew c on c.id = s.crew_id where c.title like ?",
        FIXTURE_CREW_TITLE_PATTERN);
    delete(
        "delete from point_charge where member_id in (select id from member where email like ?)",
        FIXTURE_EMAIL_PATTERN);
    delete(
        "delete mr from mission_rule mr join crew c on c.id = mr.crew_id where c.title like ?",
        FIXTURE_CREW_TITLE_PATTERN);
    delete(
        "delete from crew_participant where member_id in (select id from member where email like ?)",
        FIXTURE_EMAIL_PATTERN);
    delete(
        "delete from point_history where member_id in (select id from member where email like ?)",
        FIXTURE_EMAIL_PATTERN);
    delete(
        "delete from point_account where member_id in (select id from member where email like ?)",
        FIXTURE_EMAIL_PATTERN);
    delete("delete from crew where title like ?", FIXTURE_CREW_TITLE_PATTERN);
    delete("delete from member where email like ?", FIXTURE_EMAIL_PATTERN);
  }

  private void deleteFixtureRedisKeys() {
    redisTemplate.execute(
        (RedisCallback<Void>)
            connection -> {
              try (Cursor<byte[]> cursor = connection.scan(scanOptions())) {
                List<byte[]> keys = new ArrayList<>();
                cursor.forEachRemaining(keys::add);
                if (!keys.isEmpty()) {
                  connection.del(keys.toArray(byte[][]::new));
                }
              }
              return null;
            });
  }

  private ScanOptions scanOptions() {
    return ScanOptions.scanOptions().match(REDIS_PREFIX).count(100).build();
  }

  private void deleteFixtureS3Objects() {
    String continuationToken = null;
    do {
      String token = continuationToken;
      var page =
          s3Client.listObjectsV2(
              request -> request.bucket(bucket).prefix(S3_PREFIX).continuationToken(token));
      if (!page.contents().isEmpty()) {
        s3Client.deleteObjects(
            request ->
                request
                    .bucket(bucket)
                    .delete(
                        Delete.builder()
                            .objects(
                                page.contents().stream()
                                    .map(
                                        object ->
                                            ObjectIdentifier.builder().key(object.key()).build())
                                    .toList())
                            .build()));
      }
      continuationToken = page.nextContinuationToken();
    } while (continuationToken != null);
  }

  private void delete(String sql, String parameter) {
    jdbcTemplate.update(sql, parameter);
  }
}
