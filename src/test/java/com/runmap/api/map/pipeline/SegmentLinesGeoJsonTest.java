package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.GraphHopper;
import com.runmap.api.map.domain.AdminAreas;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class SegmentLinesGeoJsonTest {

  @Autowired GraphHopper gh;

  @Test
  void writesOneLineFeaturePerSegment(@TempDir Path dir) throws IOException {
    long[] segmentIdByEdge = SegmentBuilder.build(gh);
    Path out = dir.resolve("segments.geojson");

    SegmentLinesGeoJson.write(out, gh, segmentIdByEdge);

    String json = Files.readString(out);
    assertThat(json)
        .contains("\"FeatureCollection\"")
        .contains("\"MultiLineString\"")
        .contains("\"segmentId\"");
    int features = JsonMapper.builder().build().readTree(json).get("features").size();
    long distinctSegments = Arrays.stream(segmentIdByEdge).filter(s -> s >= 0).distinct().count();
    assertThat(features).isEqualTo((int) distinctSegments);
  }

  @Test
  void writeByGuSplitsSegmentsIntoPerGuFiles(@TempDir Path dir) throws IOException {
    AdminAreas areas = new AdminAreas("src/test/resources/fixtures/admin-2gu");
    long[] segmentIdByEdge = SegmentBuilder.build(gh);

    SegmentLinesGeoJson.writeByGu(dir, gh, segmentIdByEdge, areas);

    assertThat(dir.resolve("segments-G1.geojson")).exists();
    assertThat(dir.resolve("segments-G2.geojson")).exists();
    assertThat(Files.readString(dir.resolve("segments-G1.geojson"))).contains("MultiLineString");
    assertThat(Files.readString(dir.resolve("segments-G2.geojson"))).contains("MultiLineString");
  }

  @Test
  void writeByDongSplitsByCentroidWithDongHeader(@TempDir Path dir) throws IOException {
    AdminAreas areas = new AdminAreas("src/test/resources/fixtures/admin-2gu");
    long[] segmentIdByEdge = SegmentBuilder.build(gh);

    List<String> dongs = SegmentLinesGeoJson.writeByDong(dir, gh, segmentIdByEdge, areas);

    assertThat(dongs).contains("B"); // 서교동 영역(lng>126.918) = 동 B
    Path bFile = dir.resolve("segments-B.geojson");
    assertThat(bFile).exists();
    JsonNode b = JsonMapper.builder().build().readTree(Files.readString(bFile));
    assertThat(b.get("dong").asString()).isEqualTo("B"); // 파일명 외에 헤더에도 동 코드
    assertThat(b.get("gu").asString()).isEqualTo("G2"); // 구 포함
    assertThat(b.get("name").asString()).isEqualTo("나동"); // 동 이름 포함 (앱 번들 의존 제거)
    assertThat(b.get("features")).isNotEmpty();
    assertThat(b.get("features").get(0).get("properties").has("segmentId")).isTrue();
  }
}
