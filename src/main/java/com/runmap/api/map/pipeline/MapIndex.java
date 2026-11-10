package com.runmap.api.map.pipeline;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서울 인덱스의 총 길이 데이터 — 정복률 분모. 행정동/구별 총 길이(m). (경계 geometry는 별도 GeoJSON으로 직렬화)
 *
 * @param version OSM 스냅샷 버전
 * @param dongLengthM 행정동 코드 → 총 길이(m)
 * @param guLengthM 구 코드 → 총 길이(m)
 */
public record MapIndex(String version, Map<String, Long> dongLengthM, Map<String, Long> guLengthM) {

  public static MapIndex from(String version, RegionTotals totals) {
    return new MapIndex(version, roundToMeters(totals.byDong()), roundToMeters(totals.byGu()));
  }

  private static Map<String, Long> roundToMeters(Map<String, Double> lengths) {
    Map<String, Long> rounded = new LinkedHashMap<>();
    lengths.forEach((code, meters) -> rounded.put(code, Math.round(meters)));
    return rounded;
  }
}
