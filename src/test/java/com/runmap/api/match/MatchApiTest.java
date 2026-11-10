package com.runmap.api.match;

import static com.runmap.api.TestBodies.matchBody;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchApiTest {

  @Autowired MockMvc mvc;

  private ResultActions matches(String body) throws Exception {
    return mvc.perform(post("/api/v1/match").contentType(APPLICATION_JSON).content(body));
  }

  @Test
  void acceptsValidRequest() throws Exception {
    matches(matchBody(10))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mapVersion").value("2026-10-27"))
        .andExpect(jsonPath("$.segmentIds").isArray());
  }

  @Test
  void tooFewIs400() throws Exception {
    matches(matchBody(9))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("points"))
        .andExpect(jsonPath("$.reason").value("TOO_FEW"));
  }

  @Test
  void tooManyIs413() throws Exception {
    matches(matchBody(2001))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("TOO_MANY_POINTS"));
  }

  @Test
  void swappedLatLngNamesThePoint() throws Exception {
    matches(matchBody(10).replace("[126.920,37.555]", "[37.555,126.920]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("points[0]"))
        .andExpect(jsonPath("$.reason").value("OUT_OF_RANGE"));
  }

  @Test
  void unknownPurposeIs400() throws Exception {
    matches(matchBody(10).replace("LIVE", "BATCH"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("UNKNOWN_ENUM"));
  }

  @Test
  void outdatedMapVersionIs409() throws Exception {
    matches(matchBody(10).replace("2026-10-27", "2025-01-01"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MAP_VERSION_OUTDATED"));
  }
}
