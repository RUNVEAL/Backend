package com.runmap.api.match.domain;

/** 맵매칭 요청 목적. 동시 실행 우선순위에만 쓴다 (IMPORT · REMATCH 는 서버 전체 1건). */
public enum MatchPurpose {
  LIVE,
  IMPORT,
  REMATCH
}
