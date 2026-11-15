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
  void preservesSourceGeometryWithoutSimplifying(@TempDir Path dir) throws IOException {
    // 동별 단순화는 인접 동의 공유 경계를 제각기 줄여 "2겹"을 만든다. IndexGeoJson 은 경계를 건드리지
    // 않고 그대로 통과시키고, 단순화는 토폴로지 보존(mapshaper) 단계가 downstream 에서 맡는다.
    Path src = dir.resolve("dong.geojson");
    Files.writeString(src, denseSquareDong()); // 변 위에 촘촘한 점이 깔린 사각형 경계
    MapIndex index = new MapIndex("v", Map.of("A", 100L), Map.of("G1", 100L));
    Path out = dir.resolve("index.geojson");

    IndexGeoJson.write(out, src, index);

    assertThat(pointCount(out)).as("경계 점은 원본 그대로 유지돼야 한다").isEqualTo(pointCount(src));
    assertThat(Files.readString(out)).contains("\"lengthM\":100"); // 길이 정보는 주입
  }

  @Test
  void preservesFullCoordinatePrecision(@TempDir Path dir) throws IOException {
    // 소수 12~14자리의 원본 좌표(vuski 데이터)를 반올림 없이 그대로 보존한다.
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

    assertThat(Files.readString(out))
        .as("원본 좌표를 반올림 없이 그대로 유지해야 한다")
        .contains("126.976888842748")
        .contains("37.575650779448");
  }

  /** 각 변에 점 20개씩 깔린 사각형(=직선 위 중복점). 단순화 없이 그대로 보존되는지 확인용. */
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
