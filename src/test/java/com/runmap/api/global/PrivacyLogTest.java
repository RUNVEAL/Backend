package com.runmap.api.global;

import static com.runmap.api.TestBodies.LOOP_3K;
import static com.runmap.api.TestBodies.matchBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** API 명세 §7: 정상·깨진 JSON·범위 밖·서비스 지역 밖 요청 뒤 로그와 에러 응답에 좌표가 없어야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PrivacyLogTest {

  private static final String COORD = "\\d{2,3}\\.\\d{4,}";

  @Autowired MockMvc mvc;

  @Test
  void noCoordinatesInLogsOrErrors(CapturedOutput out) throws Exception {
    int before = out.getAll().length(); // 기동 로그(지도 범위 등)는 사용자 좌표가 아니다
    String[] bodies = {
      LOOP_3K, // 정상
      "{\"mapVersion\":\"2026-10-27\",\"start\":{\"lat\":37.512345,", // 깨진 JSON
      LOOP_3K.replace("37.5552", "95.512345"), // 범위 밖
      LOOP_3K.replace("37.5552", "37.912345"), // 서비스 지역 밖
      LOOP_3K.replace("3000", "\"37.512345\""), // 타입 불일치
    };
    for (String body : bodies) {
      String res =
          mvc.perform(post("/api/v1/routes").contentType(APPLICATION_JSON).content(body))
              .andReturn()
              .getResponse()
              .getContentAsString();
      if (!res.contains("\"routes\"")) {
        assertThat(res).doesNotContainPattern(COORD);
      }
    }
    mvc.perform(post("/api/v1/match").contentType(APPLICATION_JSON).content(matchBody(10)));
    assertThat(out.getAll().substring(before)).doesNotContainPattern(COORD);
  }
}
