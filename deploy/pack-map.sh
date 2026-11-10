#!/usr/bin/env bash
# 지도 번들(tar.gz) 만들기. 서버용(graph·areas·admin·srtm)과 앱 공개용(public)을 한 버전 폴더로 묶는다.
#
# 사용: bash deploy/pack-map.sh <graphDir> <areasDir> <adminDir> <srtmDir> <version>
#   graphDir = generateSegments 산출 폴더 (GraphHopper 그래프 + segments.bin + manifest·index·segments-*.geojson)
# 결과: build/maps/runveal-map-<version>.tar.gz
#   <version>/{graph,areas,admin,srtm,public}/ + <version>/SHA256SUMS
# public 에는 manifest.json 과 manifest 의 files 만 넣는다 (그래프 파일은 공개하지 않음).
set -euo pipefail

[ $# -eq 5 ] || { sed -n '2,8p' "$0"; exit 1; }
GRAPH=$1 AREAS=$2 ADMIN=$3 SRTM=$4 VER=$5
[[ $VER =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "버전은 YYYY-MM-DD: $VER"; exit 1; }
[ -f "$GRAPH/manifest.json" ] || { echo "manifest.json 없음: $GRAPH (generateSegments 먼저)"; exit 1; }

OUT=build/maps/runveal-map-$VER.tar.gz
[ ! -e "$OUT" ] || { echo "이미 있음 (같은 버전은 덮어쓰지 않는다): $OUT"; exit 1; }

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
D=$STAGE/$VER
mkdir -p "$D/public"
cp -R "$GRAPH" "$D/graph"
cp -R "$AREAS" "$D/areas"
cp -R "$ADMIN" "$D/admin"
cp -R "$SRTM" "$D/srtm"

MANIFEST_VER=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$GRAPH/manifest.json")
[ "$MANIFEST_VER" = "$VER" ] || { echo "manifest 버전($MANIFEST_VER) ≠ $VER"; exit 1; }
cp "$GRAPH/manifest.json" "$D/public/"
python3 -c 'import json,sys; print("\n".join(json.load(open(sys.argv[1]))["files"]))' "$GRAPH/manifest.json" |
  while read -r f; do cp "$GRAPH/$f" "$D/public/$f"; done

(cd "$D" && find . -type f ! -name SHA256SUMS | sort | xargs shasum -a 256 > SHA256SUMS)
mkdir -p build/maps
COPYFILE_DISABLE=1 tar --no-mac-metadata --no-xattrs -C "$STAGE" -czf "$OUT" "$VER" # macOS 확장 속성을 넣지 않는다
echo "완료 → $OUT ($(du -h "$OUT" | cut -f1))"
