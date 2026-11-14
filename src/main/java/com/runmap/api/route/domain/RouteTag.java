package com.runmap.api.route.domain;

/** 코스 카드 태그 (API 명세 §3.2 태그 기준). FLAT 이면 GENTLE_SLOPE 는 붙이지 않는다. */
public enum RouteTag {
  PARK,
  RIVERSIDE,
  GENTLE_SLOPE,
  FLAT
}
