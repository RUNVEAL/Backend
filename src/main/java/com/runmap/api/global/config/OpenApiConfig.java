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
        .info(
            new Info()
                .title("RUNVEAL API")
                .description("러닝 탐험 지도 백엔드 API (위치 API는 토큰 없이 호출)")
                .version("v1"));
  }
}
