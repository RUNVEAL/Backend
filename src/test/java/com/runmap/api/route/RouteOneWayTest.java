package com.runmap.api.route;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class RouteOneWayTest {

  @Autowired MockMvc mvc;

  private ResultActions oneWay(double endLat, double endLng, int distanceM) throws Exception {
    String body =
        """
        {"mapVersion":"2026-10-27","start":{"lat":37.5552,"lng":126.9236},\
        "end":{"lat":%s,"lng":%s},"distanceM":%d}"""
            .formatted(endLat, endLng, distanceM);
    return mvc.perform(post("/api/v1/routes").contentType(APPLICATION_JSON).content(body));
  }

  @Test
  void detoursWithin10Percent() throws Exception {
    // 실측: 최단 1,010m, 3km 목표에 경유지 8개 중 7개 통과
    oneWay(37.5552, 126.9326, 3000)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.routes.length()").value(greaterThan(0)))
        .andExpect(jsonPath("$.routes[*].type", everyItem(is("ONE_WAY"))))
        .andExpect(
            jsonPath(
                "$.routes[*].distanceM",
                everyItem(both(greaterThanOrEqualTo(2700)).and(lessThanOrEqualTo(3300)))));
  }

  @Test
  void destinationFartherThanDistance() throws Exception {
    oneWay(37.5640, 126.9360, 1000)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.routes").isEmpty())
        .andExpect(jsonPath("$.warnings[0]").value("DESTINATION_FARTHER_THAN_DISTANCE"))
        .andExpect(jsonPath("$.shortestDistanceM").value(greaterThan(1000)));
  }

  @Test
  void detourPointOffMapDoesNotFailWholeRequest() throws Exception {
    // 출발·도착은 지도 안. 5km 우회 경유지 일부가 fixture 밖으로 나가도 422가 아니다
    oneWay(37.5552, 126.9326, 5000).andExpect(status().isOk());
  }

  @Test
  void shortestDistanceIgnoresPreferences() throws Exception {
    String plain = oneWay(37.5640, 126.9360, 1000).andReturn().getResponse().getContentAsString();
    String body =
        """
        {"mapVersion":"2026-10-27","start":{"lat":37.5552,"lng":126.9236},\
        "end":{"lat":37.5640,"lng":126.9360},"distanceM":1000,\
        "filters":["PARK","RIVERSIDE","GENTLE_SLOPE"]}""";
    mvc.perform(post("/api/v1/routes").contentType(APPLICATION_JSON).content(body))
        .andExpect(
            jsonPath("$.shortestDistanceM")
                .value((Object) JsonPath.read(plain, "$.shortestDistanceM")));
  }

  @Test
  void endOutsideGraphIs422() throws Exception {
    oneWay(37.6200, 126.9236, 3000)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("OUT_OF_SERVICE_AREA"));
  }
}
