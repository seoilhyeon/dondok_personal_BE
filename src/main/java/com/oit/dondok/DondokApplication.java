package com.oit.dondok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class DondokApplication {

  public static void main(String[] args) {
    SpringApplication.run(DondokApplication.class, args);
  }
}
