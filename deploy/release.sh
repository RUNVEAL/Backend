#!/usr/bin/env bash
# 서버 코드 배포: 빌드(테스트 포함) → jar 업로드 → 링크 교체 → healthy 대기 → smoke.
# healthy 또는 smoke 가 실패하면 직전 jar 로 자동 롤백하고 exit 1.
#
# 사용: bash deploy/release.sh                 # 현재 HEAD 빌드·배포 (작업 트리가 깨끗해야 함)
#       bash deploy/release.sh --jar <path>    # 이미 빌드한 jar 배포 (CI). 이름은 $RELEASE_SHA 또는 HEAD
#       bash deploy/release.sh --rollback <sha> # 이미 올린 jar 로 되돌리기
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/target.env
R=/srv/runveal
ssh_() { ssh -i "$SSH_KEY" "$SSH_USER@$HOST" "$@"; }
scp_() { scp -q -i "$SSH_KEY" "$@"; }

MODE=${1:-build}
case $MODE in
  --rollback) SHA=${2:?되돌릴 sha 필요} ;;
  --jar) JAR=${2:?jar 경로 필요}; SHA=${RELEASE_SHA:-$(git rev-parse --short HEAD)} ;;
  build)
    [ -z "$(git status --porcelain)" ] || { echo "커밋 안 된 변경이 있음 — 배포 거부"; exit 1; }
    SHA=$(git rev-parse --short HEAD)
    ./gradlew build -q
    JAR=build/libs/app.jar ;;
  *) sed -n '2,7p' "$0"; exit 1 ;;
esac
[[ $SHA =~ ^[0-9a-zA-Z._-]+$ ]] || { echo "sha 형식 이상: $SHA"; exit 1; }

if [ -n "${JAR:-}" ]; then
  scp_ "$JAR" "$SSH_USER@$HOST:/tmp/app-$SHA.jar"
  ssh_ "sudo install -m 644 /tmp/app-$SHA.jar $R/releases/app-$SHA.jar && rm /tmp/app-$SHA.jar"
fi
scp_ deploy/compose.yml deploy/Caddyfile "$SSH_USER@$HOST:/tmp/"

# 링크를 바꾸고 api·caddy 를 다시 만든다 (Caddyfile 은 파일 하나 bind mount 라 재생성해야 새 내용이 들어간다)
switch() {
  ssh_ "set -e; cd $R
    [ -f releases/app-$1.jar ] || { echo 'jar 없음: $1'; exit 1; }
    [ -e maps/current ] || { echo 'maps/current 없음 — push-map.sh --activate 먼저'; exit 1; }
    sudo ln -sfn releases/app-$1.jar app.jar
    sudo docker compose up -d --force-recreate api caddy && sudo docker compose up -d"
}
verify() {
  bash deploy/wait-healthy.sh &&
    python3 deploy/smoke.py "https://$HOST" --version "$(ssh_ "sudo sed -n 's/^MAP_VERSION=//p' $R/.env")"
}

PREV=$(ssh_ "readlink $R/app.jar 2>/dev/null | sed -E 's#releases/app-(.*)\.jar#\1#'" || true)
# 설정 파일도 jar 와 함께 되돌릴 수 있게 직전 것을 .prev 로 남긴다
ssh_ "cd $R && for f in compose.yml Caddyfile; do [ -f \$f ] && sudo cp -f \$f \$f.prev; done
  sudo install -m 644 /tmp/compose.yml /tmp/Caddyfile . && rm /tmp/compose.yml /tmp/Caddyfile"
if switch "$SHA" && verify; then
  echo "배포 완료: app-$SHA (직전 app-${PREV:-없음}) → https://$HOST"
  exit 0
fi

echo "배포 실패: app-$SHA"
if [ "$MODE" != --rollback ] && [ -n "$PREV" ] && [ "$PREV" != "$SHA" ]; then
  echo "직전 app-$PREV 와 직전 설정(compose.yml · Caddyfile)으로 롤백"
  ssh_ "cd $R && for f in compose.yml Caddyfile; do [ -f \$f.prev ] && sudo cp -f \$f.prev \$f; done"
  if switch "$PREV" && verify; then
    echo "롤백 완료: app-$PREV"
  else
    echo "::error::ROLLBACK FAILED — 서버를 직접 확인하세요 (deploy/README.md 운영 명령)"
  fi
fi
exit 1
