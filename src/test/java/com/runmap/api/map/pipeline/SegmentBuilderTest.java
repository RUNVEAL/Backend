package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.GraphHopper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SegmentBuilderTest {

  @Autowired GraphHopper gh;

  @Test
  void chainsEdgesBetweenJunctionsSoSegmentsAreFewerThanEdges() {
    long[] segmentIdByEdge = SegmentBuilder.build(gh);

    assertThat(segmentIdByEdge).hasSize(gh.getBaseGraph().getEdges());
    long distinctSegments = Arrays.stream(segmentIdByEdge).distinct().count();
    assertThat(distinctSegments).isLessThan(segmentIdByEdge.length);
  }
}
