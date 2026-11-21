package com.runmap.api.route.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 경로 정보 (D9 · 경로 정보 표기 규칙). 이어진 같은 종류 조각을 합치고 200m 미만은 버린다.
 *
 * <ul>
 *   <li>새 길: 이어진 새 길은 도로명이 달라도 한 줄 (지도의 새 길 덩어리 하나 = 한 줄). 이름은 그 안에서 가장 길게 달리는 도로. 개수 상한 없이 모두 준다
 *   <li>공원·하천: 이름이 같을 때만 합치고, 긴 순 최대 6개
 * </ul>
 *
 * 결과는 시작 거리 순이고 조각끼리 겹치지 않는다.
 */
public final class Milestones {

  static final int MIN_LENGTH_M = 200;
  static final int MAX_AREA_COUNT = 6;

  /** 경로 시작부터의 누적 거리 [startM, endM). */
  public record Piece(MilestoneKind kind, String name, int startM, int endM) {
    int length() {
      return endM - startM;
    }
  }

  private Milestones() {}

  public static List<Piece> build(List<Piece> pieces) {
    List<Piece> merged = merge(pieces).stream().filter(p -> p.length() >= MIN_LENGTH_M).toList();
    Stream<Piece> newRoads = merged.stream().filter(p -> p.kind() == MilestoneKind.NEW_ROAD);
    Stream<Piece> areas =
        merged.stream()
            .filter(p -> p.kind() != MilestoneKind.NEW_ROAD)
            .sorted(Comparator.comparingInt(Piece::length).reversed())
            .limit(MAX_AREA_COUNT);
    return Stream.concat(newRoads, areas).sorted(Comparator.comparingInt(Piece::startM)).toList();
  }

  private static List<Piece> merge(List<Piece> pieces) {
    List<Piece> out = new ArrayList<>();
    Map<String, Integer> roadLengths = new HashMap<>(); // 지금 합치는 새 길 덩어리 안의 도로명별 길이
    for (Piece p : pieces) {
      Piece last = out.isEmpty() ? null : out.get(out.size() - 1);
      boolean adjacent = last != null && last.kind() == p.kind() && last.endM() == p.startM();
      boolean newRoad = p.kind() == MilestoneKind.NEW_ROAD;
      if (newRoad && !adjacent) {
        roadLengths.clear();
      }
      if (newRoad && p.name() != null) {
        roadLengths.merge(p.name(), p.length(), Integer::sum);
      }
      String name = newRoad ? longestRoad(roadLengths) : p.name();
      if (adjacent && (newRoad || Objects.equals(last.name(), p.name()))) {
        out.set(out.size() - 1, new Piece(p.kind(), name, last.startM(), p.endM()));
      } else {
        out.add(new Piece(p.kind(), name, p.startM(), p.endM()));
      }
    }
    return out;
  }

  // 같은 길이면 이름 순 — 같은 요청은 같은 결과
  private static String longestRoad(Map<String, Integer> roadLengths) {
    return roadLengths.entrySet().stream()
        .max(
            Map.Entry.<String, Integer>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
        .map(Map.Entry::getKey)
        .orElse(null);
  }
}
