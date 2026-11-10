#!/usr/bin/env bash
#
# 서울 지도 번들 생성 올인원: bbox 추출 → 공원/하천 → 지도 번들(generateSegments).
#
# 사전 준비:
#   - python3 + osmium·shapely  (pip install osmium shapely)
#   - 원본 OSM PBF (Geofabrik south-korea-latest.osm.pbf 등)
#   - 행정동 경계 GeoJSON → <ADMIN_DIR>/dong.geojson  (properties에 code·gu 필수)
#   - SRTM 타일(.hgt.zip) → <SRTM_DIR>  (서울: N37E126·N37E127)
#
# 사용:
#   bash scripts/build_map.sh <south-korea.osm.pbf>
#   조정(환경변수): BBOX, ADMIN_DIR, SRTM_DIR, OUT_DIR, WORK_DIR, MAP_VERSION
#   예) ADMIN_DIR=data/admin SRTM_DIR=data/srtm bash scripts/build_map.sh data/south-korea.osm.pbf
#
set -euo pipefail

SRC="${1:?사용: bash scripts/build_map.sh <south-korea.osm.pbf>}"
BBOX="${BBOX:-37.42 126.76 37.70 127.18}" # 서울 (south west north east)
ADMIN_DIR="${ADMIN_DIR:-admin}"           # 행정동 경계 GeoJSON (dong.geojson)
SRTM_DIR="${SRTM_DIR:-srtm}"
WORK_DIR="${WORK_DIR:-build/map-build}"
OUT_DIR="${OUT_DIR:-build/seoul}"
MAP_VERSION="${MAP_VERSION:-$(date +%F)}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

[ -f "$SRC" ] || { echo "OSM PBF 없음: $SRC"; exit 1; }
[ -f "$ADMIN_DIR/dong.geojson" ] || {
  echo "행정동 경계 없음: $ADMIN_DIR/dong.geojson (공공데이터 GeoJSON, properties에 code·gu 필요)"
  exit 1
}

SEOUL_PBF="$WORK_DIR/seoul.osm.pbf"
AREAS_DIR="$WORK_DIR/areas"
mkdir -p "$WORK_DIR"

echo "[1/3] bbox 추출 → $SEOUL_PBF"
python3 -I scripts/extract_bbox.py "$SRC" "$SEOUL_PBF" $BBOX

echo "[2/3] 공원/하천 GeoJSON → $AREAS_DIR"
python3 -I scripts/build_areas.py "$SEOUL_PBF" "$AREAS_DIR"

echo "[3/3] 지도 번들 → $OUT_DIR (version $MAP_VERSION)"
./gradlew generateSegments \
  -PosmFile="$SEOUL_PBF" \
  -PgraphLocation="$OUT_DIR" \
  -PsrtmDir="$SRTM_DIR" \
  -PareasDir="$AREAS_DIR" \
  -PadminDir="$ADMIN_DIR" \
  -PmapVersion="$MAP_VERSION"

echo ""
echo "완료 → $OUT_DIR"
ls -1 "$OUT_DIR"/manifest.json "$OUT_DIR"/index.json "$OUT_DIR"/index.geojson "$OUT_DIR"/segments-*.geojson 2>/dev/null || true
