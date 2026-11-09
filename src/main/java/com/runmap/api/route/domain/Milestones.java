package com.runmap.api.route.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 경로 정보 (D9): 종류·이름이 같은 연속 조각을 합치고, 200m 미만은 버리고, 긴 순 최대 6개를 시작 거리 순으로 돌려준다. 조각은 서로 겹치지 않는다. */
public final class Milestones {

  static final int MIN_LENGTH_M = 200;
  static final int MAX_COUNT = 6;

  /** 경로 시작부터의 누적 거리 [startM, endM). */
  public record Piece(MilestoneKind kind, String name, int startM, int endM) {
    int length() {
      return endM - startM;
    }
  }

  private Milestones() {}

  public static List<Piece> build(List<Piece> pieces) {
    List<Piece> merged = new ArrayList<>();
    for (Piece p : pieces) {
      Piece last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
      if (last != null
          && last.kind() == p.kind()
          && Objects.equals(last.name(), p.name())
          && last.endM() == p.startM()) {
        merged.set(merged.size() - 1, new Piece(p.kind(), p.name(), last.startM(), p.endM()));
      } else {
        merged.add(p);
      }
    }
    return merged.stream()
        .filter(p -> p.length() >= MIN_LENGTH_M)
        .sorted(Comparator.comparingInt(Piece::length).reversed())
        .limit(MAX_COUNT)
        .sorted(Comparator.comparingInt(Piece::startM))
        .toList();
  }
}
