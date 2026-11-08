package com.runmap.api;

/** 여러 패키지 테스트가 같이 쓰는 요청 본문. */
public final class TestBodies {

  /** 서교동 fixture 중심에서 3km 순환. */
  public static final String LOOP_3K =
      """
      {"mapVersion":"2026-10-27","start":{"lat":37.5552,"lng":126.9236},"distanceM":3000}""";

  /** JSON 객체 본문 끝에 필드를 더한다. 예: with(LOOP_3K, "\"filters\":[\"PARK\"]") */
  public static String with(String body, String fields) {
    int end = body.lastIndexOf('}');
    return body.substring(0, end) + "," + fields + "}";
  }

  private TestBodies() {}
}
