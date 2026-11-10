package com.runmap.api.global.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuardTest {

  @Autowired MockMvc mvc;

  @Test
  void bodyOver256KbIs413() throws Exception {
    String big = "{\"pad\":\"" + "x".repeat(262_144) + "\"}";
    mvc.perform(post("/api/v1/routes").contentType(APPLICATION_JSON).content(big))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
        .andExpect(header().exists("X-Request-Id"));
  }

  @Test
  void probesAreUp() throws Exception {
    mvc.perform(get("/livez")).andExpect(status().isOk());
    mvc.perform(get("/readyz")).andExpect(status().isOk());
  }
}
