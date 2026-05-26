package com.oit.dondok.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  @Test
  void openApiDocumentsJwtBearerSecurityScheme() {
    OpenAPI openAPI = new OpenApiConfig().openAPI();

    SecurityScheme bearerAuth = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");

    assertThat(openAPI.getInfo().getTitle()).isEqualTo("Dondok API");
    assertThat(bearerAuth.getType()).isEqualTo(SecurityScheme.Type.HTTP);
    assertThat(bearerAuth.getScheme()).isEqualTo("bearer");
    assertThat(bearerAuth.getBearerFormat()).isEqualTo("JWT");
    assertThat(openAPI.getSecurity())
        .anySatisfy(requirement -> assertThat(requirement).containsKey("bearerAuth"));
  }
}
