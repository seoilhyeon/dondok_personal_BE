package com.oit.dondok.infra.ses.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ses")
public record SesProperties(String fromAddress) {}
