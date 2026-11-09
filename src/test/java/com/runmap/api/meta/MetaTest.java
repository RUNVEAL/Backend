package com.runmap.api.meta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class MetaTest {

  @Autowired MockMvc mvc;

  @Test
  void metaReportsLimitsAndVersion() throws Exception {
    mvc.perform(get("/api/v1/meta"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=60"))
        .andExpect(jsonPath("$.build").isString())
        .andExpect(jsonPath("$.mapVersions.current").value("2026-10-27"))
        .andExpect(jsonPath("$.mapVersions.supported[0]").value("2026-10-27"))
        .andExpect(jsonPath("$.limits.maxBodyBytes").value(262144))
        .andExpect(jsonPath("$.limits.match.minPoints").value(10))
        .andExpect(jsonPath("$.limits.match.maxPoints").value(2000))
        .andExpect(jsonPath("$.limits.routes.distanceM.min").value(1000))
        .andExpect(jsonPath("$.limits.routes.distanceM.step").value(500))
        .andExpect(jsonPath("$.limits.routes.paceSecPerKm.default").value(390))
        .andExpect(jsonPath("$.limits.routes.maxExploredSegmentIds").value(10000))
        .andExpect(jsonPath("$.limits.routes.maxRoutes").value(3))
        .andExpect(jsonPath("$.routes.filters.length()").value(3))
        .andExpect(jsonPath("$.routes.tags.length()").value(4))
        .andExpect(jsonPath("$.routes.loopToleranceRatio").value(0.15))
        .andExpect(jsonPath("$.routes.oneWayToleranceRatio").value(0.10));
  }
}
