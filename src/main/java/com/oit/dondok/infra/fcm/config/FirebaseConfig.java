package com.oit.dondok.infra.fcm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("(!test & !integration) | integration-fcm")
@ConditionalOnExpression(
    "T(org.springframework.util.StringUtils).hasText('${app.firebase.credentials-path:}')")
@EnableConfigurationProperties(FcmProperties.class)
public class FirebaseConfig {

  @Bean
  @ConditionalOnMissingBean(FirebaseApp.class)
  public FirebaseApp firebaseApp(FcmProperties properties) throws IOException {
    synchronized (FirebaseApp.class) {
      if (!FirebaseApp.getApps().isEmpty()) {
        return FirebaseApp.getInstance();
      }
      String credentialsPath = properties.credentialsPath();
      if (credentialsPath == null || credentialsPath.isBlank()) {
        throw new IllegalStateException(
            "FIREBASE_CREDENTIALS_PATH 환경변수가 설정되지 않았습니다. "
                + "app.firebase.credentials-path를 지정하세요.");
      }
      Path credPath = Path.of(credentialsPath).toAbsolutePath().normalize();
      if (!Files.isReadable(credPath)) {
        throw new IllegalStateException("Firebase 인증 파일을 읽을 수 없습니다: " + credPath);
      }
      GoogleCredentials credentials;
      try (FileInputStream credStream = new FileInputStream(credPath.toFile())) {
        credentials = GoogleCredentials.fromStream(credStream);
      }
      FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
      log.info("[FCM] FirebaseApp 초기화 완료");
      return FirebaseApp.initializeApp(options);
    }
  }

  @Bean
  @ConditionalOnMissingBean(FirebaseMessaging.class)
  public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }
}
