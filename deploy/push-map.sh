#!/usr/bin/env bash
# 지도 번들 업로드 · 전환. 같은 버전은 덮어쓰지 않는다. 앱용 파일 공개 → (활성화 시) 서버 전환 순서.
#
# 사용: bash deploy/push-map.sh build/maps/runveal-map-<ver>.tar.gz [--activate]
#       bash deploy/push-map.sh --activate-existing <ver>    # 이미 올린 버전으로 전환 (롤백)
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/target.env
R=/srv/runveal
ssh_() { ssh -i "$SSH_KEY" "$SSH_USER@$HOST" "$@"; }

if [ "${1:-}" = --activate-existing ]; then
  VER=${2:?버전 필요}
  ACTIVATE=1
else
  TAR=${1:?tar 경로 필요}
  VER=$(basename "$TAR" | sed -E 's/^runveal-map-([0-9-]+)\.tar\.gz$/\1/')
  [[ $VER =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "파일 이름이 runveal-map-<YYYY-MM-DD>.tar.gz 가 아님"; exit 1; }
  ACTIVATE=$([ "${2:-}" = --activate ] && echo 1 || echo 0)
  ssh_ "[ ! -e $R/maps/$VER ]" || { echo "이미 있음 (덮어쓰기 금지): maps/$VER"; exit 1; }
  scp -i "$SSH_KEY" "$TAR" "$SSH_USER@$HOST:/tmp/map-$VER.tar.gz"
  ssh_ "set -e; cd $R
    sudo tar --no-same-owner -xzf /tmp/map-$VER.tar.gz -C maps && rm /tmp/map-$VER.tar.gz
    (cd maps/$VER && sudo sha256sum -c --quiet SHA256SUMS) && echo 'checksum ok'
    sudo chown 1000:1000 maps/$VER/graph   # GraphHopper 가 gh.lock 을 만든다. 파일은 root 소유 그대로
    sudo cp -R maps/$VER/public public-maps/$VER"
  echo "올림: maps/$VER · 공개: /maps/$VER/"
fi

if [ "$ACTIVATE" = 1 ]; then
  ssh_ "set -e; cd $R; [ -d maps/$VER ]
    sudo sed -i 's/^MAP_VERSION=.*/MAP_VERSION=$VER/' .env
    sudo ln -sfn $VER maps/current
    if [ -e app.jar ]; then sudo docker compose up -d --force-recreate api; fi"
  if ssh_ "[ -e $R/app.jar ]"; then
    bash deploy/wait-healthy.sh
    python3 deploy/smoke.py "https://$HOST" --version "$VER"
  fi
  echo "활성화: 지도 $VER"
fi
