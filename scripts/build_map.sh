#!/usr/bin/env bash
#
# 전국 지도 번들 생성 올인원: (선택) bbox 크롭 → 공원/하천 → 지도 번들(generateSegments).
# 기본은 전국(크롭 없음). 테스트·PoC로 좁히려면 BBOX 를 지정한다.
#
# 사전 준비:
#   - 행정동 경계 GeoJSON → <ADMIN_DIR>/dong.geojson (properties 에 code·gu 필수)
#   - OSM PBF: 인자로 주거나, 없으면 Geofabrik 전국본을 자동 다운로드
#   - 공원/하천(osmium)·좌표 처리는 이 스크립트가 venv 를 자동으로 만들어 처리한다
#     (시스템 파이썬 안 건드림. pyosmium 휠이 있는 python3.13 을 우선 사용)
#   - SRTM 고도 타일은 GraphHopper 가 빌드 중 자동 내려받는다
#
# 사용:
#   bash scripts/build_map.sh                         # 전국, OSM 자동 다운로드
#   bash scripts/build_map.sh path/to/south-korea.osm.pbf
#   BBOX="37.42 126.76 37.70 127.18" bash scripts/build_map.sh korea.pbf   # 서울만(테스트)
#
# 조정(환경변수): BBOX, ADMIN_DIR, SRTM_DIR, AREAS_DIR, OUT_DIR, WORK_DIR, MAP_VERSION, HEAP, VENV
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

WORK_DIR="${WORK_DIR:-build/mapdata}"
SRC="${1:-$WORK_DIR/south-korea-latest.osm.pbf}"
ADMIN_DIR="${ADMIN_DIR:-$WORK_DIR/admin}"
SRTM_DIR="${SRTM_DIR:-$WORK_DIR/srtm}"
AREAS_DIR="${AREAS_DIR:-$WORK_DIR/areas}"
OUT_DIR="${OUT_DIR:-build/nationwide}"
MAP_VERSION="${MAP_VERSION:-$(date +%F)}"
HEAP="${HEAP:-13g}"       # 전국 그래프 import 대비. 램 적으면 낮추고, 넉넉하면 올린다
BBOX="${BBOX:-}"          # 비어 있으면 전국(크롭 안 함)
VENV="${VENV:-$WORK_DIR/venv}"
OSM_URL="https://download.geofabrik.de/asia/south-korea-latest.osm.pbf"

mkdir -p "$WORK_DIR" "$SRTM_DIR" "$AREAS_DIR"

# 공원/하천·bbox 처리용 파이썬: venv 를 한 번 만들어 osmium·shapely 를 넣고 재사용한다.
ensure_venv() {
  [ -x "$VENV/bin/python3" ] && return
  local py="python3"
  command -v python3.13 >/dev/null 2>&1 && py="python3.13"  # pyosmium 휠 있는 버전 우선
  echo "  venv 생성 + osmium·shapely 설치 ($VENV, $py)"
  "$py" -m venv "$VENV"
  "$VENV/bin/pip" install -q --upgrade pip
  "$VENV/bin/pip" install -q osmium shapely
}
venv_py() { ensure_venv; "$VENV/bin/python3" -I "$@"; }

# 0) OSM PBF 확보 (없으면 전국본 자동 다운로드)
if [ ! -f "$SRC" ]; then
  echo "[0/3] OSM 없음 → Geofabrik 전국본 다운로드: $SRC"
  curl -L -o "$SRC" "$OSM_URL"
fi

[ -f "$ADMIN_DIR/dong.geojson" ] || {
  echo "행정동 경계 없음: $ADMIN_DIR/dong.geojson (공공데이터 GeoJSON, properties 에 code·gu 필요)"
  exit 1
}

# 1) (선택) bbox 크롭 — 기본은 전국이라 건너뛴다
PBF="$SRC"
if [ -n "$BBOX" ]; then
  PBF="$WORK_DIR/cropped.osm.pbf"
  echo "[1/3] bbox 크롭 ($BBOX) → $PBF"
  venv_py scripts/extract_bbox.py "$SRC" "$PBF" $BBOX
else
  echo "[1/3] 전국 (bbox 크롭 생략)"
fi

# 2) 공원/하천 GeoJSON → custom areas
echo "[2/3] 공원/하천 GeoJSON → $AREAS_DIR"
venv_py scripts/build_areas.py "$PBF" "$AREAS_DIR"

# 3) 지도 번들 (그래프를 새로 import 해야 areas 가 반영되므로 기존 출력은 지운다)
echo "[3/3] 지도 번들 → $OUT_DIR (version $MAP_VERSION, heap $HEAP)"
rm -rf "$OUT_DIR"
./gradlew generateSegments \
  -PosmFile="$PBF" \
  -PgraphLocation="$OUT_DIR" \
  -PsrtmDir="$SRTM_DIR" \
  -PareasDir="$AREAS_DIR" \
  -PadminDir="$ADMIN_DIR" \
  -PmapVersion="$MAP_VERSION" \
  -Pheap="$HEAP"

echo ""
echo "완료 → $OUT_DIR"
ls -1 "$OUT_DIR"/manifest.json "$OUT_DIR"/index.json "$OUT_DIR"/index.geojson "$OUT_DIR"/segments-*.geojson 2>/dev/null | head
