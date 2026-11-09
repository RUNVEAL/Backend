package com.runmap.api.match.service;

import com.runmap.api.match.dto.MatchResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 맵매칭 엔진.
 *
 * <p>주인: 우선님. 본문만 교체한다. 지금은 앱 연동용 고정 응답(인정 구간 없음, 입력 경로 그대로). 입력은 이미 검증됨(10~2000점, 범위 안). 엔진 실패는
 * {@code BusinessException(MATCH_FAILED | OUT_OF_SERVICE_AREA)}로 던지고, 예외 메시지에 좌표를 넣지 않는다.
 */
@Service
public class MatchService {

  public MatchResponse match(String mapVersion, List<double[]> points) {
    return new MatchResponse(mapVersion, List.of(), points, 0, List.of());
  }
}
