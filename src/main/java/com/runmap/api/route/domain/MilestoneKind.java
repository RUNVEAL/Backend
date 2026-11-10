package com.runmap.api.route.domain;

/** 경로 정보 종류 (D9). 같은 구간이 여럿에 해당하면 PARK · RIVER > NEW_ROAD > ROAD. */
public enum MilestoneKind {
  ROAD,
  PARK,
  RIVER,
  NEW_ROAD
}
