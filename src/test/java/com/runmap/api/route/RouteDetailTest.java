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
class RouteDetailTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  private JsonNode firstRoute(String body) throws Exception {
    String res =
        mvc.perform(post("/api/v1/routes").contentType(APPLICATION_JSON).content(body))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(res).at("/routes/0");
  }

  private static double parkShare(JsonNode route) {
    double park = 0;
    for (JsonNode m : route.get("milestones")) {
      if ("PARK".equals(m.get("kind").asString())) {
        park += m.get("endM").asInt() - m.get("startM").asInt();
      }
    }
    return park / route.get("distanceM").asInt();
  }

  @Test
  void detailFieldsArePresent() throws Exception {
    JsonNode r =
        firstRoute(
            with(LOOP_3K, "\"filters\":[\"PARK\",\"GENTLE_SLOPE\"],\"exploredSegmentIds\":[]"));
    assertThat(r.get("elevationGainM").isInt()).isTrue();
    JsonNode prof = r.get("elevationProfile");
    assertThat(prof.get(0).get(0).asInt()).isZero();
    assertThat(prof.get(prof.size() - 1).get(0).asInt()).isEqualTo(r.get("distanceM").asInt());
    assertThat(r.get("tags").size()).isLessThanOrEqualTo(3);
    assertThat(r.get("milestones").size()).isBetween(1, 6);
    r.get("milestones")
        .forEach(
            m ->
                assertThat(m.get("endM").asInt() - m.get("startM").asInt())
                    .isGreaterThanOrEqualTo(200));
  }

  @Test
  void roadMilestonesCarryStreetNames() throws Exception {
    JsonNode r = firstRoute(LOOP_3K); // 탐험 목록 없음 → NEW_ROAD 없음, ROAD 이름이 보인다
    boolean namedRoad = false;
    for (JsonNode m : r.get("milestones")) {
      namedRoad |= "ROAD".equals(m.get("kind").asString()) && !m.get("name").isNull();
    }
    assertThat(namedRoad).isTrue();
  }

  @Test
  void newRoadMilestonesCarryStreetNames() throws Exception {
    // 탐험 0개(신규 유저) → 거의 전 구간이 NEW_ROAD. 도로명이 있으면 같이 와야 한다
    String res =
        mvc.perform(
                post("/api/v1/routes")
                    .contentType(APPLICATION_JSON)
                    .content(with(LOOP_3K, "\"exploredSegmentIds\":[]")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<JsonNode> newRoads = new ArrayList<>();
    boolean park = false;
    for (JsonNode route : JSON.readTree(res).get("routes")) {
      for (JsonNode m : route.get("milestones")) {
        JsonNode name = m.get("name");
        assertThat(name.isNull() || !name.asString().isBlank()).isTrue(); // "" 대신 null
        String kind = m.get("kind").asString();
        park |= "PARK".equals(kind);
        if ("NEW_ROAD".equals(kind)) {
          newRoads.add(m);
        }
      }
    }
    assertThat(newRoads).isNotEmpty();
    assertThat(newRoads).anyMatch(m -> !m.get("name").isNull());
    assertThat(park).isTrue(); // 공원(경의선 숲길)은 새 길이어도 PARK 유지
  }

  @Test
  void parkFilterIncreasesParkShare() throws Exception {
    // 선호 가중치일 뿐 보장은 아니지만, fixture 중심에서는 실측 0 → 0.49 (결정론적)
    double without = parkShare(firstRoute(LOOP_3K));
    double withPark = parkShare(firstRoute(with(LOOP_3K, "\"filters\":[\"PARK\"]")));
    assertThat(withPark).isGreaterThan(without);
  }
}
