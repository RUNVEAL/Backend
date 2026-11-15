package com.runmap.api.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.graphhopper.GraphHopper;
import com.runmap.api.map.config.MapProperties;
import com.runmap.api.map.pipeline.SegmentsFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SegmentIdsLoadingTest {

  @Autowired GraphHopper gh;
  @Autowired MapProperties props;
  @Autowired Environment env;

  @Test
  void loadsSegmentsBinFromGraphLocationWhenPresent() throws IOException {
    int edges = gh.getBaseGraph().getEdges();
    long[] mapping = new long[edges];
    Arrays.fill(mapping, 42L); // 모든 edge를 segment 42로
    SegmentsFile.write(Path.of(props.graphLocation(), "segments.bin"), mapping);

    SegmentIds segmentIds = new SegmentIds(gh, props, env);

    assertThat(segmentIds.segmentOf(0)).isEqualTo(42L);
    assertThat(segmentIds.edgesOf(List.of(42L))).hasSize(edges);
  }

  @Test
  void failsFastInProdWhenSegmentsBinMissing(@TempDir Path emptyDir) {
    MapProperties missing = new MapProperties("v", "", emptyDir.toString(), "", "");
    Environment prod = new MockEnvironment(); // 활성 프로파일 없음 = 운영

    assertThatThrownBy(() -> new SegmentIds(gh, missing, prod))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void usesIdentityInDevWhenSegmentsBinMissing(@TempDir Path emptyDir) {
    MapProperties missing = new MapProperties("v", "", emptyDir.toString(), "", "");
    MockEnvironment dev = new MockEnvironment();
    dev.setActiveProfiles("test");

    SegmentIds segmentIds = new SegmentIds(gh, missing, dev);

    assertThat(segmentIds.segmentOf(0)).isEqualTo(0L); // identity (segmentId = edgeId)
  }
}
