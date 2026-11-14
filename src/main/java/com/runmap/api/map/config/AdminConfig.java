package com.runmap.api.map.config;

import com.runmap.api.map.domain.AdminAreas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 행정동/구 경계(공공데이터 GeoJSON) 조회 빈. {@code runveal.map.admin-dir} 미설정 시 빈 조회(graceful). */
@Configuration
public class AdminConfig {

  @Bean
  public AdminAreas adminAreas(@Value("${runveal.map.admin-dir:}") String adminDir) {
    return new AdminAreas(adminDir);
  }
}
