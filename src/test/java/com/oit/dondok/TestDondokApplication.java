package com.oit.dondok;

import org.springframework.boot.SpringApplication;

public class TestDondokApplication {

  public static void main(String[] args) {
    SpringApplication.from(DondokApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
