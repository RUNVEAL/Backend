#!/usr/bin/env bash
# GitHub Actions → Azure 키 없는 로그인(OIDC) + 배포용 SSH 키 + 레포 변수 설정. 다시 돌려도 같은 결과.
# 전제: az login (구독 Owner), deploy/target.env, GH_TOKEN(레포 admin).
#
# 사용: GH_TOKEN=$(gh auth token --user inoaole) bash deploy/cicd-setup.sh
set -euo pipefail
export PYTHONWARNINGS=ignore
cd "$(dirname "$0")/.."
source deploy/target.env
REPO=RUNVEAL/Backend
MI=runveal-gha
GHA_KEY=$HOME/.ssh/runveal_gha
NSG=$(az network nsg list -g "$RG" --query "[0].name" -o tsv)

echo "[1/5] Managed Identity $MI"
az identity create -g "$RG" -n "$MI" -o none
CLIENT_ID=$(az identity show -g "$RG" -n "$MI" --query clientId -o tsv)
PRINCIPAL_ID=$(az identity show -g "$RG" -n "$MI" --query principalId -o tsv)

echo "[2/5] federated credential: main(배포) · develop(전원)"
for b in main develop; do
  az identity federated-credential show -g "$RG" --identity-name "$MI" -n "gha-$b" -o none 2>/dev/null ||
    az identity federated-credential create -g "$RG" --identity-name "$MI" -n "gha-$b" \
    --issuer https://token.actions.githubusercontent.com \
    --subject "repo:$REPO:ref:refs/heads/$b" --audiences api://AzureADTokenExchange -o none
done

echo "[3/5] 최소 권한: VM · NSG 스코프만"
VM_ID=$(az vm show -g "$RG" -n "$VM" --query id -o tsv)
NSG_ID=$(az network nsg show -g "$RG" -n "$NSG" --query id -o tsv)
for pair in "Virtual Machine Contributor|$VM_ID" "Network Contributor|$NSG_ID"; do
  az role assignment create --assignee-object-id "$PRINCIPAL_ID" --assignee-principal-type ServicePrincipal \
    --role "${pair%%|*}" --scope "${pair#*|}" -o none
done

echo "[4/5] 배포 전용 SSH 키 → VM authorized_keys"
[ -f "$GHA_KEY" ] || ssh-keygen -t ed25519 -N "" -C "runveal-gha-deploy" -f "$GHA_KEY" -q
PUB=$(cat "$GHA_KEY.pub")
ssh -i "$SSH_KEY" "$SSH_USER@$HOST" "grep -qxF '$PUB' ~/.ssh/authorized_keys || echo '$PUB' >> ~/.ssh/authorized_keys"

# 호스트 키는 이미 신뢰한 로컬 known_hosts 와 같을 때만 등록 (keyscan 결과를 그냥 믿지 않는다)
SCANNED=$(ssh-keyscan -t ed25519 "$HOST" 2>/dev/null | grep -v '^#')
TRUSTED=$(ssh-keygen -F "$HOST" -f ~/.ssh/known_hosts | grep -v '^#' | grep ssh-ed25519 | awk '{print $3}')
[ -n "$TRUSTED" ] && [ "$(echo "$SCANNED" | awk '{print $3}')" = "$TRUSTED" ] ||
  { echo "호스트 키가 known_hosts 와 다름 — 중단"; exit 1; }

echo "[5/5] GitHub 변수·비밀 ($REPO)"
gh variable set AZURE_CLIENT_ID -R "$REPO" -b "$CLIENT_ID"
gh variable set AZURE_TENANT_ID -R "$REPO" -b "$(az account show --query tenantId -o tsv)"
gh variable set AZURE_SUBSCRIPTION_ID -R "$REPO" -b "$(az account show --query id -o tsv)"
gh variable set VM_HOST -R "$REPO" -b "$HOST"
gh variable set VM_KNOWN_HOSTS -R "$REPO" -b "$SCANNED"
gh secret set VM_SSH_KEY -R "$REPO" < "$GHA_KEY"
echo "완료: MI principal $PRINCIPAL_ID"
