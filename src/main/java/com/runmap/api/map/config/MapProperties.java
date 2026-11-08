package com.runmap.api.map.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지도 데이터 위치와 버전. 운영은 환경변수 RUNVEAL_MAP_* 로 주입한다.
 *
 * @param version OSM 스냅샷 날짜 (YYYY-MM-DD). API의 mapVersion
 * @param osmFile OSM PBF 경로
 * @param graphLocation GraphHopper 그래프 캐시 디렉터리. 없으면 PBF에서 import
 * @param srtmDir SRTM 고도 타일(.hgt.zip) 디렉터리
 * @param areasDir 공원·하천 GeoJSON 디렉터리 (scripts/build_areas.py 산출물)
 */
@ConfigurationProperties("runveal.map")
public record MapProperties(
    String version, String osmFile, String graphLocation, String srtmDir, String areasDir) {}
