#!/usr/bin/env bash
# GitHub Actions 러너 IP 하나에만 SSH 22 를 잠깐 연다 / 닫는다 (평소엔 사람 IP 만 열려 있음).
# 사용: bash deploy/gha-ssh.sh open|close   (azure/login 이후, deploy/target.env 필요)
# 배포는 concurrency 로 한 번에 하나뿐이라, open 은 이전 실행이 남긴 gha-ssh-* 규칙을 먼저 지운다.
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/target.env
NSG=${NSG:-runveal-apiNSG} # 이름 고정: CI 아이덴티티는 NSG 에만 권한이 있어 RG 목록 조회를 못 한다
RULE=gha-ssh-${GITHUB_RUN_ID:?GitHub Actions 안에서만}

stale() {
  az network nsg rule list -g "$RG" --nsg-name "$NSG" --query "[?starts_with(name,'gha-ssh-')].name" -o tsv
}

case ${1:-} in
  open)
    for r in $(stale); do az network nsg rule delete -g "$RG" --nsg-name "$NSG" -n "$r" -o none && echo "남은 규칙 삭제: $r"; done
    IP=$(curl -fsS --retry 3 https://api.ipify.org)
    az network nsg rule create -g "$RG" --nsg-name "$NSG" -n "$RULE" --priority 1100 \
      --access Allow --protocol Tcp --direction Inbound --destination-port-ranges 22 \
      --source-address-prefixes "$IP/32" -o none
    # 방금 켠 VM 이면 sshd 가 늦게 뜬다. 호스트 키 불일치는 숨기지 않는다
    for _ in $(seq 24); do
      err=$(ssh -i "$SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=5 "$SSH_USER@$HOST" true 2>&1) &&
        { echo "ssh ok ($IP)"; exit 0; }
      case $err in *"Host key verification failed"*|*"REMOTE HOST IDENTIFICATION"*) echo "$err"; exit 1 ;; esac
      sleep 5
    done
    echo "ssh 연결 실패: $err"; exit 1 ;;
  close)
    az network nsg rule delete -g "$RG" --nsg-name "$NSG" -n "$RULE" -o none
    left=$(stale)
    [ -z "$left" ] || { echo "::error::남은 SSH 규칙: $left"; exit 1; }
    echo "closed $RULE" ;;
  *) echo "사용: $0 open|close"; exit 1 ;;
esac
