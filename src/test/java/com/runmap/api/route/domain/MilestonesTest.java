package com.runmap.api.route.domain;

import static com.runmap.api.route.domain.MilestoneKind.NEW_ROAD;
import static com.runmap.api.route.domain.MilestoneKind.PARK;
import static com.runmap.api.route.domain.MilestoneKind.RIVER;
import static java.util.Comparator.comparingInt;
import static org.assertj.core.api.Assertions.assertThat;

import com.runmap.api.route.domain.Milestones.Piece;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MilestonesTest {

  @Test
  void mergesDropsShortSortedByStart() {
    List<Piece> in =
        List.of(
            new Piece(PARK, "서울숲", 0, 700),
            new Piece(PARK, "서울숲", 700, 1400), // 합쳐서 0~1400
            new Piece(NEW_ROAD, null, 1400, 1550), // 150m → 버림
            new Piece(RIVER, "중랑천", 1550, 2600));
    assertThat(Milestones.build(in))
        .containsExactly(new Piece(PARK, "서울숲", 0, 1400), new Piece(RIVER, "중랑천", 1550, 2600));
  }

  @Test
  void adjacentNewRoadsBecomeOneStretchNamedByLongestRoad() {
    // 지도에 이어진 새 길 한 덩어리 = 경로 정보 한 줄. 이름은 그 안에서 가장 길게 달리는 도로
    List<Piece> in =
        List.of(
            new Piece(NEW_ROAD, "A길", 0, 100),
            new Piece(NEW_ROAD, null, 100, 300),
            new Piece(NEW_ROAD, "B길", 300, 600),
            new Piece(NEW_ROAD, "A길", 600, 700));
    assertThat(Milestones.build(in)).containsExactly(new Piece(NEW_ROAD, "B길", 0, 700));
  }

  @Test
  void unnamedNewRoadStretchKeepsNullName() {
    List<Piece> in =
        List.of(new Piece(NEW_ROAD, null, 0, 300), new Piece(NEW_ROAD, null, 300, 600));
    assertThat(Milestones.build(in)).containsExactly(new Piece(NEW_ROAD, null, 0, 600));
  }

  @Test
  void everyNewRoadStretchIsKeptBeyondTheCap() {
    // 새 길 8덩어리(사이사이 공원) — 새 길은 개수 상한 없이 모두, 공원·하천만 긴 순 6개
    List<Piece> in =
        IntStream.range(0, 8)
            .boxed()
            .flatMap(
                i ->
                    java.util.stream.Stream.of(
                        new Piece(NEW_ROAD, null, i * 1000, i * 1000 + 300),
                        new Piece(PARK, "공원" + i, i * 1000 + 300, i * 1000 + 600 + i * 10)))
            .toList();
    List<Piece> out = Milestones.build(in);
    assertThat(out).filteredOn(p -> p.kind() == NEW_ROAD).hasSize(8);
    assertThat(out)
        .filteredOn(p -> p.kind() == PARK)
        .hasSize(6)
        .extracting(Piece::name)
        .doesNotContain("공원0", "공원1");
    assertThat(out).isSortedAccordingTo(comparingInt(Piece::startM));
  }

  @Test
  void mergesOnlyAdjacentPieces() {
    List<Piece> in =
        List.of(
            new Piece(PARK, "공원", 0, 300),
            new Piece(RIVER, "하천", 300, 600),
            new Piece(PARK, "공원", 600, 900));
    assertThat(Milestones.build(in)).hasSize(3);
  }
}
