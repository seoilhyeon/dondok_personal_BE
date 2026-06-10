package com.oit.dondok.infra.fcm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.firebase")
public record FcmProperties(String credentialsPath) {}
