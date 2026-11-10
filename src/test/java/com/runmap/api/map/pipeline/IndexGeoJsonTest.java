package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexGeoJsonTest {

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
}
