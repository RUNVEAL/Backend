package com.runmap.api.global.web;

import static com.runmap.api.TestBodies.LOOP_3K;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "runveal.rate-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitTest {

  @Autowired MockMvc mvc;

  private ResultActions routesFrom(String ip) throws Exception {
    return mvc.perform(
        post("/api/v1/routes")
            .header("X-Forwarded-For", ip)
            .contentType(APPLICATION_JSON)
            .content(LOOP_3K));
  }

  @Test
  void routesLimitedPerRealClientIp() throws Exception {
    for (int i = 0; i < 10; i++) {
      routesFrom("203.0.113.7").andExpect(status().isOk());
    }
    routesFrom("203.0.113.7")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(header().exists("Retry-After"));
    routesFrom("198.51.100.9").andExpect(status().isOk()); // 다른 IP 영향 없음 = 실제 IP를 읽는다
  }
}
