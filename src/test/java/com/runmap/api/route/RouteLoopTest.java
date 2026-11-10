package com.runmap.api.route;

import static com.runmap.api.TestBodies.LOOP_3K;
import static com.runmap.api.TestBodies.with;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RouteLoopTest {

  @Autowired MockMvc mvc;

  private ResultActions routes(String body) throws Exception {
    return mvc.perform(post("/api/v1/routes").contentType(APPLICATION_JSON).content(body));
  }

  @Test
  void loopReturnsUpToThreeRoutesWithin15Percent() throws Exception {
    routes(LOOP_3K)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mapVersion").value("2026-10-27"))
        .andExpect(
            jsonPath("$.routes.length()").value(both(greaterThan(0)).and(lessThanOrEqualTo(3))))
        .andExpect(jsonPath("$.routes[*].type", everyItem(is("LOOP"))))
        .andExpect(
            jsonPath(
                "$.routes[*].distanceM",
                everyItem(both(greaterThanOrEqualTo(2550)).and(lessThanOrEqualTo(3450)))))
        .andExpect(jsonPath("$.routes[0].durationSec").isNumber())
        .andExpect(jsonPath("$.routes[0].path[0].length()").value(2))
        .andExpect(jsonPath("$.routes[0].routeId").value(matchesPattern("r_[0-9a-f]{8}")))
        .andExpect(jsonPath("$.warnings").isEmpty())
        .andExpect(jsonPath("$.shortestDistanceM").doesNotExist());
  }

  @Test
  void sameRequestSameResponse() throws Exception {
    String a = routes(LOOP_3K).andReturn().getResponse().getContentAsString();
    String b = routes(LOOP_3K).andReturn().getResponse().getContentAsString();
    assertThat(a).isEqualTo(b);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"start\":{\"lat\":37.5552,\"lng\":126.9236}}                                          | mapVersion   | REQUIRED",
        "{\"mapVersion\":\"2026-10-27\"}                                                         | start        | REQUIRED",
        "{\"mapVersion\":\"2026-10-27\",\"start\":{\"lat\":127.02,\"lng\":37.49}}                 | start.lat    | OUT_OF_RANGE",
        "{\"mapVersion\":\"2026-10-27\",\"start\":{\"lat\":\"NaN\",\"lng\":126.9}}                | start.lat    | NOT_FINITE",
        "{\"mapVersion\":\"2026-10-27\",\"start\":{\"lat\":37.55,\"lng\":126.92},\"distanceM\":3200}   | distanceM    | NOT_MULTIPLE_OF_STEP",
        "{\"mapVersion\":\"2026-10-27\",\"start\":{\"lat\":37.55,\"lng\":126.92},\"distanceM\":500}    | distanceM    | OUT_OF_RANGE",
        "{\"mapVersion\":\"2026-10-27\",\"start\":{\"lat\":37.55,\"lng\":126.92},\"paceSecPerKm\":100} | paceSecPerKm | OUT_OF_RANGE",
        "{\"mapVersion\":\"2026-10-27\",\"start\":{\"lat\":37.55,\"lng\":126.92},\"filters\":[\"SIGNAL\"]} | filters[0] | UNKNOWN_ENUM",
      })
  void invalidRequestsReportFirstViolation(String body, String field, String reason)
      throws Exception {
    routes(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.field").value(field))
        .andExpect(jsonPath("$.reason").value(reason));
  }

  @Test
  void tooManySegmentsIs413() throws Exception {
    String ids = LongStream.range(0, 10_001).mapToObj(Long::toString).collect(joining(","));
    routes(
            "{\"mapVersion\":\"2026-10-27\",\"start\":{\"lat\":37.5552,\"lng\":126.9236},"
                + "\"exploredSegmentIds\":["
                + ids
                + "]}")
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("TOO_MANY_SEGMENTS"));
  }

  @Test
  void outdatedMapVersionIs409WithSupported() throws Exception {
    routes(LOOP_3K.replace("2026-10-27", "2025-01-01"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MAP_VERSION_OUTDATED"))
        .andExpect(jsonPath("$.supportedMapVersions[0]").value("2026-10-27"));
  }

  @Test
  void startOutsideGraphIs422() throws Exception {
    routes(LOOP_3K.replace("37.5552", "37.6200"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("OUT_OF_SERVICE_AREA"));
  }

  @Test
  void unknownRequestFieldsAreIgnored() throws Exception {
    routes(with(LOOP_3K, "\"futureField\":1")).andExpect(status().isOk());
  }
}
