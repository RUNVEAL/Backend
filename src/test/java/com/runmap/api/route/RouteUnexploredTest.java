package com.runmap.api.route;

import static com.runmap.api.TestBodies.LOOP_3K;
import static com.runmap.api.TestBodies.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RouteUnexploredTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  private JsonNode routes(String body) throws Exception {
    String res =
        mvc.perform(post("/api/v1/routes").contentType(APPLICATION_JSON).content(body))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(res);
  }

  @Test
  void penaltySteersAwayFromExploredRoads() throws Exception {
    JsonNode first = routes(LOOP_3K).at("/routes/0");
    List<Long> explored = new ArrayList<>();
    first.get("segmentIds").forEach(n -> explored.add(n.asLong()));

    JsonNode r = routes(with(LOOP_3K, "\"exploredSegmentIds\":" + explored)).at("/routes/0");
    assertThat(r.get("newRoadM").asInt()).isGreaterThan(r.get("distanceM").asInt() / 2);
    assertThat(r.get("newRoadRatio").asDouble()).isBetween(0.5, 1.0);
  }

  @Test
  void emptyExploredMeansAllNew() throws Exception {
    // 구간당 1회 계산(명세 §3.2)이라 같은 길을 되짚는 코스는 distanceM 보다 작을 수 있다
    JsonNode r = routes(with(LOOP_3K, "\"exploredSegmentIds\":[]")).at("/routes/0");
    int distanceM = r.get("distanceM").asInt();
    assertThat(r.get("newRoadM").asInt()).isBetween((int) (distanceM * 0.8), distanceM);
    assertThat(r.get("newRoadRatio").asDouble())
        .isEqualTo(Math.round(r.get("newRoadM").asDouble() / distanceM * 100) / 100.0);
  }

  @Test
  void missingExploredMeansNull() throws Exception {
    JsonNode r = routes(LOOP_3K).at("/routes/0");
    assertThat(r.get("newRoadM").isNull()).isTrue();
    assertThat(r.get("newRoadRatio").isNull()).isTrue();
  }

  @Test
  void preferUnexploredFalseStillReportsNewRoad() throws Exception {
    JsonNode r =
        routes(with(LOOP_3K, "\"preferUnexplored\":false,\"exploredSegmentIds\":[1,2,3]"))
            .at("/routes/0");
    assertThat(r.get("newRoadM").isNumber()).isTrue();
  }
}
