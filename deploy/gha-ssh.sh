#!/usr/bin/env bash
# GitHub Actions 러너 IP 하나에만 SSH 22 를 잠깐 연다 / 닫는다 (평소엔 사람 IP 만 열려 있음).
# 사용: bash deploy/gha-ssh.sh open|close   (azure/login 이후, deploy/target.env 필요)
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/target.env
RULE=gha-ssh-${GITHUB_RUN_ID:?GitHub Actions 안에서만}
NSG=$(az network nsg list -g "$RG" --query "[0].name" -o tsv)

case ${1:-} in
  open)
    IP=$(curl -fsS https://api.ipify.org)
    az network nsg rule create -g "$RG" --nsg-name "$NSG" -n "$RULE" --priority $((1100 + GITHUB_RUN_ID % 100)) \
      --access Allow --protocol Tcp --direction Inbound --destination-port-ranges 22 \
      --source-address-prefixes "$IP/32" -o none
    # 방금 켠 VM 이면 sshd 가 늦게 뜬다
    for _ in $(seq 24); do
      ssh -i "$SSH_KEY" -o ConnectTimeout=5 "$SSH_USER@$HOST" true 2>/dev/null && { echo "ssh ok ($IP)"; exit 0; }
      sleep 5
    done
    echo "ssh 연결 실패"; exit 1 ;;
  close)
    az network nsg rule delete -g "$RG" --nsg-name "$NSG" -n "$RULE" -o none || true
    echo "closed $RULE" ;;
  *) echo "사용: $0 open|close"; exit 1 ;;
esac
