package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class IndexGeoJsonTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  void injectsDongTotalLengthIntoBoundaryFeatures(@TempDir Path dir) throws IOException {
    MapIndex index = new MapIndex("2026-10-27", Map.of("A", 123L, "B", 457L), Map.of("G1", 580L));
    Path out = dir.resolve("index.geojson");

    IndexGeoJson.write(out, Path.of("src/test/resources/fixtures/admin/dong.geojson"), index);

    String json = Files.readString(out);
    assertThat(json).contains("\"lengthM\":123").contains("\"lengthM\":457"); // 동 A·B 총 길이
    assertThat(json).contains("\"Polygon\""); // 경계 geometry 보존
    assertThat(json).contains("\"code\":\"A\"").contains("\"code\":\"B\"");
  }

  @Test
  void simplifiesDenseBoundaries(@TempDir Path dir) throws IOException {
    Path src = dir.resolve("dong.geojson");
    Files.writeString(src, denseSquareDong()); // 변 위에 촘촘한 점이 깔린 사각형 경계
    MapIndex index = new MapIndex("v", Map.of("A", 100L), Map.of("G1", 100L));
    Path out = dir.resolve("index.geojson");

    IndexGeoJson.write(out, src, index);

    int before = pointCount(src);
    int after = pointCount(out);
    assertThat(after).as("직선 위 점이 제거돼 좌표가 크게 줄어야 한다").isLessThan(before / 2);
    assertThat(after).as("사각형 모서리는 남아야 한다(유효 폴리곤)").isGreaterThanOrEqualTo(4);
    assertThat(Files.readString(out)).contains("\"lengthM\":100"); // 길이 정보는 유지
  }

  @Test
  void roundsCoordinatesToSixDecimals(@TempDir Path dir) throws IOException {
    // 소수 12~14자리의 과한 정밀도 좌표 (원본 vuski 데이터처럼)
    Path src = dir.resolve("dong.geojson");
    Files.writeString(
        src,
        "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\","
            + "\"properties\":{\"code\":\"A\",\"gu\":\"G1\",\"name\":\"가동\"},"
            + "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[["
            + "[126.976888842748,37.575650779448],[126.986888842748,37.575650779448],"
            + "[126.986888842748,37.585650779448],[126.976888842748,37.585650779448],"
            + "[126.976888842748,37.575650779448]]]}}]}");
    MapIndex index = new MapIndex("v", Map.of("A", 100L), Map.of("G1", 100L));
    Path out = dir.resolve("index.geojson");

    IndexGeoJson.write(out, src, index);

    JsonNode root = MAPPER.readTree(Files.readString(out));
    assertRoundedToSixDecimals(root.get("features").get(0).get("geometry").get("coordinates"));
  }

  private static void assertRoundedToSixDecimals(JsonNode coords) {
    if (coords.get(0).isNumber()) {
      for (JsonNode v : coords) {
        double c = v.asDouble();
        assertThat(Math.abs(c - Math.round(c * 1e6) / 1e6))
            .as("좌표는 소수 6자리로 반올림돼야 한다: " + c)
            .isLessThan(1e-9);
      }
    } else {
      for (JsonNode c : coords) {
        assertRoundedToSixDecimals(c);
      }
    }
  }

  /** 각 변에 점 20개씩 깔린 사각형(=직선 위 중복점). 단순화되면 모서리만 남는다. */
  private static String denseSquareDong() {
    double[][] corners = {{0, 0}, {0.01, 0}, {0.01, 0.01}, {0, 0.01}};
    StringBuilder ring = new StringBuilder("[");
    for (int c = 0; c < 4; c++) {
      double[] a = corners[c];
      double[] b = corners[(c + 1) % 4];
      for (int i = 0; i < 20; i++) {
        double t = i / 20.0;
        if (c > 0 || i > 0) {
          ring.append(',');
        }
        ring.append('[')
            .append(a[0] + (b[0] - a[0]) * t)
            .append(',')
            .append(a[1] + (b[1] - a[1]) * t)
            .append(']');
      }
    }
    ring.append(",[0,0]]"); // 닫기
    return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\","
        + "\"properties\":{\"code\":\"A\",\"gu\":\"G1\",\"name\":\"가동\"},"
        + "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":["
        + ring
        + "]}}]}";
  }

  private static int pointCount(Path geojson) throws IOException {
    JsonNode root = MAPPER.readTree(Files.readString(geojson));
    int[] n = {0};
    for (JsonNode f : root.get("features")) {
      count(f.get("geometry").get("coordinates"), n);
    }
    return n[0];
  }

  private static void count(JsonNode coords, int[] n) {
    if (coords.get(0).isNumber()) {
      n[0]++;
    } else {
      for (JsonNode c : coords) {
        count(c, n);
      }
    }
  }
}
