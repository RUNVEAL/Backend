#!/usr/bin/env bash
# VM 초기 설정. 여러 번 돌려도 같은 결과 (root 로 실행, SITE_ADDRESS 필요).
#   ssh … "sudo SITE_ADDRESS=<host> bash -s" < deploy/vm-bootstrap.sh
set -euo pipefail
: "${SITE_ADDRESS:?SITE_ADDRESS 필요}"

if ! command -v docker >/dev/null; then
  apt-get update -q
  DEBIAN_FRONTEND=noninteractive apt-get install -y -q docker.io docker-compose-v2
fi
systemctl enable --now docker

ROOT=/srv/runveal
mkdir -p $ROOT/releases $ROOT/maps $ROOT/public-maps

# 비밀번호는 처음 한 번만 만든다 (DB 볼륨과 맞아야 한다)
if [ ! -f $ROOT/.env ]; then
  umask 077
  printf 'SITE_ADDRESS=%s\nMAP_VERSION=\nPOSTGRES_PASSWORD=%s\nRATE_LIMIT_ENABLED=true\n' \
    "$SITE_ADDRESS" "$(openssl rand -hex 24)" > $ROOT/.env
fi
echo "bootstrap ok: $(docker --version)"
