package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.GraphHopper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
}
