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
MI=runveal-gha             # 배포 (main 만)
POWER_MI=runveal-gha-power # 전원 (develop · main)
POWER_ROLE="RUNVEAL VM Power"
GHA_KEY=$HOME/.ssh/runveal_gha
NSG=$(az network nsg list -g "$RG" --query "[0].name" -o tsv)

echo "[1/5] Managed Identity $MI"
az identity create -g "$RG" -n "$MI" -o none
CLIENT_ID=$(az identity show -g "$RG" -n "$MI" --query clientId -o tsv)
PRINCIPAL_ID=$(az identity show -g "$RG" -n "$MI" --query principalId -o tsv)

fed() { # <identity> <branch>
  az identity federated-credential show -g "$RG" --identity-name "$1" -n "gha-$2" -o none 2>/dev/null ||
    az identity federated-credential create -g "$RG" --identity-name "$1" -n "gha-$2" \
      --issuer https://token.actions.githubusercontent.com \
      --subject "repo:$REPO:ref:refs/heads/$2" --audiences api://AzureADTokenExchange -o none
}
echo "[2/5] federated credential: 배포 아이덴티티는 main 만"
fed "$MI" main
# develop 은 누구나 push 할 수 있다 → 배포 권한(VM Contributor = runCommand 로 root)을 주지 않는다
az identity federated-credential delete -g "$RG" --identity-name "$MI" -n gha-develop --yes -o none 2>/dev/null || true

echo "[3/5] 최소 권한: VM · NSG 스코프만"
VM_ID=$(az vm show -g "$RG" -n "$VM" --query id -o tsv)
NSG_ID=$(az network nsg show -g "$RG" -n "$NSG" --query id -o tsv)
for pair in "Virtual Machine Contributor|$VM_ID" "Network Contributor|$NSG_ID"; do
  az role assignment create --assignee-object-id "$PRINCIPAL_ID" --assignee-principal-type ServicePrincipal \
    --role "${pair%%|*}" --scope "${pair#*|}" -o none
done

echo "[3b/5] 전원 아이덴티티 $POWER_MI: 켜기·끄기·조회만 (develop · main)"
az identity create -g "$RG" -n "$POWER_MI" -o none
POWER_CLIENT_ID=$(az identity show -g "$RG" -n "$POWER_MI" --query clientId -o tsv)
POWER_PRINCIPAL_ID=$(az identity show -g "$RG" -n "$POWER_MI" --query principalId -o tsv)
fed "$POWER_MI" develop
fed "$POWER_MI" main
RG_ID=$(az group show -n "$RG" --query id -o tsv)
if ! az role definition list --name "$POWER_ROLE" --query "[0].name" -o tsv | grep -q .; then
  az role definition create -o none --role-definition "{
    \"Name\": \"$POWER_ROLE\", \"Description\": \"runveal-api VM start/deallocate/read only\",
    \"Actions\": [\"Microsoft.Compute/virtualMachines/read\", \"Microsoft.Compute/virtualMachines/instanceView/read\",
                  \"Microsoft.Compute/virtualMachines/start/action\", \"Microsoft.Compute/virtualMachines/deallocate/action\"],
    \"AssignableScopes\": [\"$RG_ID\"] }"
  sleep 20 # 역할 정의가 퍼질 때까지
fi
az role assignment create --assignee-object-id "$POWER_PRINCIPAL_ID" --assignee-principal-type ServicePrincipal \
  --role "$POWER_ROLE" --scope "$VM_ID" -o none

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
gh variable set AZURE_POWER_CLIENT_ID -R "$REPO" -b "$POWER_CLIENT_ID"
gh variable set AZURE_TENANT_ID -R "$REPO" -b "$(az account show --query tenantId -o tsv)"
gh variable set AZURE_SUBSCRIPTION_ID -R "$REPO" -b "$(az account show --query id -o tsv)"
gh variable set VM_HOST -R "$REPO" -b "$HOST"
gh variable set VM_KNOWN_HOSTS -R "$REPO" -b "$SCANNED"
gh secret set VM_SSH_KEY -R "$REPO" < "$GHA_KEY"
echo "완료: 배포 MI $PRINCIPAL_ID · 전원 MI $POWER_PRINCIPAL_ID"
