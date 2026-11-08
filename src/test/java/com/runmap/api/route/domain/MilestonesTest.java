package com.runmap.api.route.domain;

import static com.runmap.api.route.domain.MilestoneKind.NEW_ROAD;
import static com.runmap.api.route.domain.MilestoneKind.PARK;
import static com.runmap.api.route.domain.MilestoneKind.RIVER;
import static com.runmap.api.route.domain.MilestoneKind.ROAD;
import static java.util.Comparator.comparingInt;
import static org.assertj.core.api.Assertions.assertThat;

import com.runmap.api.route.domain.Milestones.Piece;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MilestonesTest {

  @Test
  void mergesDropsShortKeepsTop6SortedByStart() {
    List<Piece> in =
        List.of(
            new Piece(ROAD, "서울숲길", 0, 700),
            new Piece(ROAD, "서울숲길", 700, 1400), // 합쳐서 0~1400
            new Piece(NEW_ROAD, null, 1400, 1550), // 150m → 버림
            new Piece(PARK, "서울숲", 1550, 2600),
            new Piece(RIVER, "중랑천", 2600, 3400));
    assertThat(Milestones.build(in))
        .containsExactly(
            new Piece(ROAD, "서울숲길", 0, 1400),
            new Piece(PARK, "서울숲", 1550, 2600),
            new Piece(RIVER, "중랑천", 2600, 3400));
  }

  @Test
  void atMostSixLongest() {
    List<Piece> in =
        IntStream.range(0, 8)
            .mapToObj(i -> new Piece(ROAD, "길" + i, i * 1000, i * 1000 + 300 + i * 10))
            .toList();
    List<Piece> out = Milestones.build(in);
    assertThat(out).hasSize(6).extracting(Piece::name).doesNotContain("길0", "길1");
    assertThat(out).isSortedAccordingTo(comparingInt(Piece::startM));
  }

  @Test
  void mergesOnlyAdjacentPieces() {
    List<Piece> in =
        List.of(
            new Piece(ROAD, "A길", 0, 300),
            new Piece(PARK, "공원", 300, 600),
            new Piece(ROAD, "A길", 600, 900));
    assertThat(Milestones.build(in)).hasSize(3);
  }
}
