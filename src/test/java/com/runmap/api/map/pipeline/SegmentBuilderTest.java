package com.runmap.api.map.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.GraphHopper;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
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

  @Test
  void assignsContentDerivedIdsNotPositionalCounters() {
    long[] segmentIdByEdge = SegmentBuilder.build(gh);

    Set<Long> ids = Arrays.stream(segmentIdByEdge).boxed().collect(Collectors.toSet());
    Set<Long> positional = LongStream.range(0, ids.size()).boxed().collect(Collectors.toSet());
    assertThat(ids).isNotEqualTo(positional);
  }

  @Test
  void mergesParallelChainsSharingEndpointsAndClose() {
    long[] pairKeys = {100L, 100L, 200L}; // 체인 0·1 같은 교차점쌍, 체인 2 다른 쌍
    long[] ids = {50L, 70L, 90L};
    double[][] centroids = {{37.5, 126.9}, {37.50001, 126.9}, {37.6, 127.0}}; // 0·1 ~1m, 2 멀리

    long[] merged = SegmentBuilder.mergeParallels(pairKeys, ids, centroids);

    assertThat(merged[0]).isEqualTo(merged[1]); // 병합
    assertThat(merged[2]).isEqualTo(90L); // 그대로
  }

  @Test
  void doesNotMergeSameEndpointsButFarApart() {
    long[] pairKeys = {100L, 100L};
    long[] ids = {50L, 70L};
    double[][] centroids = {{37.5, 126.9}, {37.52, 126.9}}; // ~2km

    long[] merged = SegmentBuilder.mergeParallels(pairKeys, ids, centroids);

    assertThat(merged[0]).isNotEqualTo(merged[1]); // 멀면 병합 안 함
  }
}
