package com.runmap.api.route.domain;

/** 경로 정보 종류 (D9). 같은 구간이 여럿에 해당하면 PARK · RIVER > NEW_ROAD. 일반 경유 도로는 목록에 넣지 않는다 (경로 정보 표기 규칙). */
public enum MilestoneKind {
  PARK,
  RIVER,
  NEW_ROAD
}
