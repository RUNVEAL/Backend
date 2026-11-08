package com.runmap.api.map;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.runmap.api.map.domain.AreaEncodedValues;
import com.runmap.api.map.domain.AreaNames;
import com.runmap.api.map.domain.MapGraphFactory;
import com.runmap.api.map.domain.SegmentIds;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MapGraphTest {

  @Autowired GraphHopper gh;
  @Autowired AreaNames areaNames;
  @Autowired SegmentIds segmentIds;

  @Test
  void loadsFixtureWithElevationAndAreaTags() {
    GHResponse res =
        gh.route(
            new GHRequest(37.5552, 126.9236, 37.5552, 126.9326)
                .setProfile(MapGraphFactory.PROFILE));
    assertThat(res.hasErrors()).as(res.getErrors().toString()).isFalse();
    assertThat(res.getBest().getPoints().is3D()).isTrue();
    assertThat(gh.getEncodingManager().hasEncodedValue(AreaEncodedValues.NEAR_PARK)).isTrue();
    assertThat(gh.getEncodingManager().hasEncodedValue(AreaEncodedValues.NEAR_RIVER)).isTrue();
  }

  @Test
  void namesAreasByPoint() {
    assertThat(areaNames.nameAt(37.552241, 126.923243, "park")).contains("홍익문화공원");
    assertThat(areaNames.nameAt(37.565021, 126.909624, "river")).contains("홍제천");
    assertThat(areaNames.nameAt(37.5552, 126.9236, "river")).isEmpty();
  }

  @Test
  void segmentIdsAreIdentityUntilPipelineLands() {
    assertThat(segmentIds.segmentOf(42)).isEqualTo(42L);
    assertThat(segmentIds.edgesOf(List.of(1L, 2L, -5L, 1L << 40))).containsExactlyInAnyOrder(1, 2);
  }
}
