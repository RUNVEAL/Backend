package com.runmap.api.map.domain;

import com.runmap.api.global.error.BusinessException;
import com.runmap.api.global.error.ErrorCode;
import com.runmap.api.map.config.MapProperties;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 요청 mapVersion 판정. 형식이 틀리면 400, 탑재하지 않은 버전이면 409.
 *
 * <p>ponytail: 1차는 현재 버전 1개만 탑재. 지도 갱신을 시작하면 직전 버전도 함께 올린다(API 명세 §2.1).
 */
@Component
public class MapVersions {

  private static final Pattern FORMAT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  private final String current;

  public MapVersions(MapProperties props) {
    this.current = props.version();
  }

  public static void requireFormat(String mapVersion) {
    if (mapVersion == null || mapVersion.isBlank()) {
      throw BusinessException.invalid("mapVersion", "REQUIRED");
    }
    if (!FORMAT.matcher(mapVersion).matches()) {
      throw BusinessException.invalid("mapVersion", "INVALID_TYPE");
    }
  }

  public void check(String mapVersion) {
    requireFormat(mapVersion);
    if (!supported().contains(mapVersion)) {
      throw new BusinessException(ErrorCode.MAP_VERSION_OUTDATED)
          .with("supportedMapVersions", supported());
    }
  }

  public String current() {
    return current;
  }

  public List<String> supported() {
    return List.of(current);
  }
}
