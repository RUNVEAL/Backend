package com.runmap.api.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import(ErrorResponseTest.Probe.class)
class ErrorResponseTest {

  private static final String UUID = "[0-9a-f-]{36}";

  @Autowired MockMvc mvc;

  @RestController
  static class Probe {
    @PostMapping("/test/invalid")
    void invalid() {
      throw BusinessException.invalid("start.lat", "OUT_OF_RANGE");
    }

    @PostMapping("/test/busy")
    void busy() {
      throw new BusinessException(ErrorCode.SERVER_BUSY).retryAfter(3);
    }

    @PostMapping("/test/boom")
    void boom() {
      throw new IllegalStateException("point 37.555201,126.923611 failed");
    }

    @PostMapping("/test/echo")
    Map<String, Object> echo(@RequestBody Map<String, Object> body) {
      return body;
    }
  }

  @Test
  void businessErrorFollowsSpecShape() throws Exception {
    mvc.perform(post("/test/invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.field").value("start.lat"))
        .andExpect(jsonPath("$.reason").value("OUT_OF_RANGE"))
        .andExpect(jsonPath("$.message").isString())
        .andExpect(jsonPath("$.requestId").value(matchesPattern(UUID)))
        .andExpect(header().string("X-Request-Id", matchesPattern(UUID)))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void busyHasRetryAfter() throws Exception {
    mvc.perform(post("/test/busy"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVER_BUSY"))
        .andExpect(header().string("Retry-After", "3"));
  }

  @Test
  void unexpectedErrorHidesMessage(CapturedOutput out) throws Exception {
    mvc.perform(post("/test/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("37.555201"))));
    assertThat(out.getAll()).doesNotContain("37.555201").contains("IllegalStateException");
  }

  @Test
  void malformedJsonIs400WithReason() throws Exception {
    mvc.perform(post("/test/echo").contentType(APPLICATION_JSON).content("{\"lat\": 37.555201,"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("MALFORMED_JSON"))
        .andExpect(content().string(not(containsString("37.555201"))));
  }

  @Test
  void frameworkErrorsKeepStatus() throws Exception {
    mvc.perform(get("/test/echo")).andExpect(status().isMethodNotAllowed());
    mvc.perform(post("/nope")).andExpect(status().isNotFound());
  }
}
