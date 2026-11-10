package com.runmap.api.map.pipeline;

import java.util.List;

/**
 * 지도 번들 manifest — 앱이 파일 선택·로컬 판정에 쓴다.
 *
 * @param version OSM 스냅샷 버전
 * @param files 번들에 포함된 파일 이름
 * @param matchDistanceM 로컬 색칠 판정: 도로선 이내 거리(m) (A 소유)
 * @param segmentCoverage 로컬 색칠 판정: 구간 커버 비율 (A 소유)
 * @param trimMinM 전송 전 양끝 절단 최소(m) (B 소유)
 * @param trimMaxM 전송 전 양끝 절단 최대(m) (B 소유)
 */
public record Manifest(
    String version,
    List<String> files,
    int matchDistanceM,
    double segmentCoverage,
    int trimMinM,
    int trimMaxM) {

  /** 기본 판정 기준값(도로선 15m·구간 60%)과 절단 길이(300~800m)로 만든다. */
  public static Manifest of(String version, List<String> files) {
    return new Manifest(version, List.copyOf(files), 15, 0.60, 300, 800);
  }
}
