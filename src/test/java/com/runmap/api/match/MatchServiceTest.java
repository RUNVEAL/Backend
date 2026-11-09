package com.runmap.api.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.util.PointList;
import com.runmap.api.map.domain.MapGraphFactory;
import com.runmap.api.match.dto.MatchResponse;
import com.runmap.api.match.service.MatchService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MatchServiceTest {

  @Autowired GraphHopper gh;
  @Autowired MatchService matchService;

  /** 서교동 fixture 도로를 실제로 따라가는 트랙을 라우팅으로 만든다 (= 노이즈 없는 on-road GPS). */
  private List<double[]> onRoadTrack() {
    PointList pts =
        gh.route(
                new GHRequest(37.5552, 126.9236, 37.5552, 126.9326)
                    .setProfile(MapGraphFactory.PROFILE))
            .getBest()
            .getPoints();
    List<double[]> track = new ArrayList<>();
    for (int i = 0; i < pts.size(); i++) {
      track.add(new double[] {pts.getLon(i), pts.getLat(i)});
    }
    return track;
  }

  @Test
  void matchesOnRoadTrackToSegments() {
    MatchResponse res = matchService.match("2026-10-27", onRoadTrack());

    assertThat(res.segmentIds()).isNotEmpty();
    assertThat(res.matchedDistanceM()).isGreaterThan(0);
  }
}
