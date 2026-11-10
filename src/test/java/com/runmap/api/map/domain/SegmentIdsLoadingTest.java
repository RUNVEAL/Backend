package com.runmap.api.map.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.GraphHopper;
import com.runmap.api.map.config.MapProperties;
import com.runmap.api.map.pipeline.SegmentsFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SegmentIdsLoadingTest {

  @Autowired GraphHopper gh;
  @Autowired MapProperties props;

  @Test
  void loadsSegmentsBinFromGraphLocationWhenPresent() throws IOException {
    int edges = gh.getBaseGraph().getEdges();
    long[] mapping = new long[edges];
    Arrays.fill(mapping, 42L); // 모든 edge를 segment 42로
    SegmentsFile.write(Path.of(props.graphLocation(), "segments.bin"), mapping);

    SegmentIds segmentIds = new SegmentIds(gh, props);

    assertThat(segmentIds.segmentOf(0)).isEqualTo(42L);
    assertThat(segmentIds.edgesOf(List.of(42L))).hasSize(edges);
  }
}
