package com.oit.dondok.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("!load-test")
@EnableScheduling
public class SchedulingConfiguration {}
