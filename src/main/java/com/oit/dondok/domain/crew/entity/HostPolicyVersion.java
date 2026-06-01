package com.oit.dondok.domain.crew.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum HostPolicyVersion {
  HOST_POLICY_V1("v1");

  private final String value;

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static HostPolicyVersion fromValue(String value) {
    for (HostPolicyVersion v : values()) {
      if (v.value.equalsIgnoreCase(value)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Unknown HostPolicyVersion: " + value);
  }
}
