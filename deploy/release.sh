#!/usr/bin/env bash
# 서버 코드 배포: 빌드(테스트 포함) → jar 업로드 → 링크 교체 → healthy 대기 → smoke.
#
# 사용: bash deploy/release.sh                 # 현재 HEAD 배포 (작업 트리가 깨끗해야 함)
#       bash deploy/release.sh --rollback <sha> # 이미 올린 jar 로 되돌리기
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/target.env
R=/srv/runveal
ssh_() { ssh -i "$SSH_KEY" "$SSH_USER@$HOST" "$@"; }
scp_() { scp -q -i "$SSH_KEY" "$@"; }

if [ "${1:-}" = --rollback ]; then
  SHA=${2:?되돌릴 sha 필요}
else
  git diff --quiet && git diff --cached --quiet || { echo "커밋 안 된 변경이 있음 — 배포 거부"; exit 1; }
  SHA=$(git rev-parse --short HEAD)
  ./gradlew build -q
  scp_ build/libs/app.jar "$SSH_USER@$HOST:/tmp/app-$SHA.jar"
  ssh_ "sudo install -m 644 /tmp/app-$SHA.jar $R/releases/app-$SHA.jar && rm /tmp/app-$SHA.jar"
fi

scp_ deploy/compose.yml deploy/Caddyfile "$SSH_USER@$HOST:/tmp/"
ssh_ "set -e; cd $R
  sudo install -m 644 /tmp/compose.yml /tmp/Caddyfile . && rm /tmp/compose.yml /tmp/Caddyfile
  [ -f releases/app-$SHA.jar ] || { echo 'jar 없음: $SHA'; exit 1; }
  [ -e maps/current ] || { echo 'maps/current 없음 — push-map.sh --activate 먼저'; exit 1; }
  sudo ln -sfn releases/app-$SHA.jar app.jar
  sudo docker compose up -d --force-recreate api && sudo docker compose up -d"
bash deploy/wait-healthy.sh
VER=$(ssh_ "sudo sed -n 's/^MAP_VERSION=//p' $R/.env")
python3 deploy/smoke.py "https://$HOST" --version "$VER"
echo "배포 완료: app-$SHA (지도 $VER) → https://$HOST"
