package com.runmap.api.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger(OpenAPI) 문서 메타데이터 설정. 제목·설명·버전은 여기서 바꾼다. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info().title("러닝 코스 앱 API").description("러닝 코스 공유 앱 백엔드 API 문서").version("v1"));
  }
}
