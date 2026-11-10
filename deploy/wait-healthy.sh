#!/usr/bin/env bash
# VM 의 api 컨테이너가 healthy 가 될 때까지 최대 5분 기다린다. 실패하면 마지막 로그(에러 줄)를 보여준다.
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/target.env
ssh -i "$SSH_KEY" "$SSH_USER@$HOST" 'cd /srv/runveal
  for _ in $(seq 60); do
    [ "$(sudo docker compose ps api --format "{{.Health}}")" = healthy ] && { echo "api healthy"; exit 0; }
    sleep 5
  done
  echo "api 가 5분 안에 healthy 가 안 됨"; sudo docker compose logs api --tail 200 | grep -E "ERROR|Caused by" | tail -5; exit 1'
