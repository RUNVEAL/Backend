package com.runmap.api.route.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphhopper.GraphHopper;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.runmap.api.route.domain.MilestoneKind;
import com.runmap.api.route.domain.Milestones.Piece;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RouteAssemblerNearbyRoadTest {

  @Autowired GraphHopper gh;
  @Autowired RouteAssembler assembler;

  @Test
  void unnamedNewRoadTakesNearestNamedRoad() {
    PointList alley = unnamedEdgeGeometry();
    Piece in = new Piece(MilestoneKind.NEW_ROAD, null, 0, 300);

    Piece out = assembler.nameByNearbyRoad(in, alley);

    // 앱이 "새 길" 옆에 name 을 그대로 쓴다 → "새 길 서울숲2길 근처 골목"
    assertThat(out.name()).endsWith(" 근처 골목").hasSizeGreaterThan(" 근처 골목".length());
    assertThat(out.startM()).isZero();
    assertThat(out.endM()).isEqualTo(300);
  }

  @Test
  void areaPiecesAreUntouched() {
    Piece park = new Piece(MilestoneKind.PARK, null, 0, 300);

    assertThat(assembler.nameByNearbyRoad(park, unnamedEdgeGeometry())).isEqualTo(park);
  }

  // fixture 그래프에서 이름 없는 아무 edge (서교동 일대엔 이름 없는 골목이 많다)
  private PointList unnamedEdgeGeometry() {
    EdgeIterator it = gh.getBaseGraph().getAllEdges();
    while (it.next()) {
      String name = it.getName();
      if ((name == null || name.isBlank()) && it.getDistance() > 20) {
        return it.fetchWayGeometry(FetchMode.ALL);
      }
    }
    throw new IllegalStateException("fixture 에 이름 없는 edge 가 없다");
  }
}
