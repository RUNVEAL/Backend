#!/usr/bin/env bash
# Azure 서울 VM 1대 만들기 (한 번 실행). 이미 있는 자원은 건너뛴다.
# 전제: az login 완료, 구독 = Azure for Students (koreacentral 허용).
# 결과: deploy/target.env (HOST · SSH_USER · SSH_KEY · RG · VM). 비밀 없음.
#
# 사용: bash deploy/azure-setup.sh
#   조정(환경변수): LOCATION(koreacentral) SIZE(Standard_B2s_v2) DNS_LABEL(runveal-<랜덤>)
set -euo pipefail
export PYTHONWARNINGS=ignore

cd "$(dirname "$0")/.."
LOCATION=${LOCATION:-koreacentral}
SIZE=${SIZE:-Standard_B2s_v2}
RG=runveal-rg
VM=runveal-api
SSH_USER=runveal
SSH_KEY=$HOME/.ssh/runveal_azure
TARGET=deploy/target.env

[ -f "$TARGET" ] && source "$TARGET"
DNS_LABEL=${DNS_LABEL:-runveal-$(openssl rand -hex 2)}

[ -f "$SSH_KEY" ] || ssh-keygen -t ed25519 -N "" -C "runveal-deploy" -f "$SSH_KEY" -q
MY_IP=$(curl -fsS https://api.ipify.org)

echo "[1/4] 리소스 그룹 $RG ($LOCATION)"
az group create -n "$RG" -l "$LOCATION" -o none

echo "[2/4] VM $VM ($SIZE)"
if ! az vm show -g "$RG" -n "$VM" -o none 2>/dev/null; then
  az vm create -g "$RG" -n "$VM" -l "$LOCATION" --size "$SIZE" \
    --image Canonical:ubuntu-24_04-lts:server:latest \
    --admin-username "$SSH_USER" --ssh-key-values "$SSH_KEY.pub" \
    --os-disk-size-gb 30 --storage-sku StandardSSD_LRS \
    --public-ip-sku Standard --public-ip-address-allocation static \
    --public-ip-address-dns-name "$DNS_LABEL" \
    --nsg-rule NONE -o none
fi

echo "[3/4] 방화벽: 80·443 전체, 22 는 내 IP($MY_IP)만"
NSG=$(az network nsg list -g "$RG" --query "[0].name" -o tsv)
az network nsg rule create -g "$RG" --nsg-name "$NSG" -n allow-web --priority 900 \
  --access Allow --protocol Tcp --direction Inbound --destination-port-ranges 80 443 -o none
az network nsg rule create -g "$RG" --nsg-name "$NSG" -n allow-ssh-me --priority 1000 \
  --access Allow --protocol Tcp --direction Inbound --destination-port-ranges 22 \
  --source-address-prefixes "$MY_IP/32" -o none

HOST=$(az vm show -d -g "$RG" -n "$VM" --query fqdns -o tsv)
cat > "$TARGET" <<EOF
RG=$RG
VM=$VM
DNS_LABEL=$DNS_LABEL
HOST=$HOST
SSH_USER=$SSH_USER
SSH_KEY=$SSH_KEY
EOF

echo "[4/4] VM 초기 설정 (docker · 폴더 · .env)"
for _ in $(seq 1 30); do
  ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5 "$SSH_USER@$HOST" true 2>/dev/null && break
  sleep 5
done
ssh -i "$SSH_KEY" "$SSH_USER@$HOST" "sudo SITE_ADDRESS=$HOST bash -s" < deploy/vm-bootstrap.sh

echo "완료 → https://$HOST  (target: $TARGET)"
