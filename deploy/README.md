# 배포 운영 가이드

서버 1대(Azure 서울, `Standard_B2s_v2` 2 vCPU · 8GB) + Docker Compose: `caddy`(HTTPS · `/maps/*` 정적 서빙) · `api`(jar) · `postgres`(회원 DB).
jar 와 지도는 이미지에 굽지 않는다. VM `/srv/runveal` 에서 링크(`app.jar`, `maps/current`)만 바꿔 끼운다.

```
/srv/runveal/
├─ compose.yml · Caddyfile · .env (600, 비밀번호)
├─ releases/app-<sha>.jar      app.jar → releases/app-<sha>.jar
├─ maps/<ver>/{graph,areas,admin,srtm,public}   maps/current → <ver>
└─ public-maps/<ver>/          → https://<host>/maps/<ver>/manifest.json
```

접속 정보는 `deploy/target.env` (커밋 안 함, `azure-setup.sh` 가 만든다).

## 프라이버시 (바꾸면 안 되는 것)

- Caddy 에 `log` 지시어를 넣지 않는다 → 접속 로그(IP·시각) 없음. Caddy 가 직접 만든 5xx 에러 로그(`http.log.error`, IP 포함)도 전역 설정으로 끈다
- `api` 는 `-XX:-HeapDumpOnOutOfMemoryError` (힙 덤프 금지). 에러 추적 도구를 붙이면 본문 수집을 끈다
- `/livez` · `/readyz` · `/actuator/**` 는 외부에서 404 (Compose healthcheck 만 내부에서 쓴다)
- 확인: `python3 deploy/smoke.py https://<host> --spoof-check` + 로그 grep (아래)

## 처음 세팅 (한 번)

```bash
az login                          # Azure for Students 구독
bash deploy/azure-setup.sh        # RG · VM · 방화벽(80/443 전체, 22 내 IP만) · docker · .env
```

## 자동 배포 (CI/CD)

`main` 에 머지되면 `.github/workflows/deploy.yml` 이 돈다: 테스트 → jar → (VM 이 꺼져 있으면 켜고) → 이 러너 IP 에만 SSH 를 잠깐 열고 → `release.sh --jar` → smoke → SSH 닫기.
배포가 실패하면(healthy·smoke) `release.sh` 가 **직전 jar 로 자동 롤백**하고 job 은 빨간불로 끝난다.

- Azure 로그인은 OIDC (키 없음). 아이덴티티 2개:
  - `runveal-gha` (배포): **main 토큰만**, VM Contributor(VM) + Network Contributor(NSG). VM Contributor 는 run-command 로 root 가 되므로 main 에만 준다
  - `runveal-gha-power` (전원): develop·main, 커스텀 역할 `RUNVEAL VM Power` = VM 읽기·켜기·끄기 4개 권한만
- 장기 비밀은 배포 전용 SSH 키(`VM_SSH_KEY`) 하나. 레포 쓰기 권한 = 운영 권한이라고 보고 협업자를 관리한다 (무료 플랜이라 브랜치 보호 없음)
- 호스트 키는 `VM_KNOWN_HOSTS` 에 고정 (처음 보는 키는 거부)
- 다시 배포: `gh workflow run deploy.yml -R RUNVEAL/Backend --ref main`
- 설정을 다시 만들 때: `GH_TOKEN=$(gh auth token --user inoaole) bash deploy/cicd-setup.sh`
- 흐름: `feature/*` → PR → `develop` (CI) → PR → `main` (배포)

## VM 켜기·끄기 (어디서든)

```bash
gh workflow run vm-power.yml -R RUNVEAL/Backend -f action=start   # 켜고 API 응답까지 확인
gh workflow run vm-power.yml -R RUNVEAL/Backend -f action=stop    # deallocate (요금 멈춤)
gh workflow run vm-power.yml -R RUNVEAL/Backend -f action=status
gh run watch -R RUNVEAL/Backend $(gh run list -R RUNVEAL/Backend -w vm-power.yml -L 1 --json databaseId -q '.[0].databaseId')
```

- 자동: 매일 **02:07 KST 끄기 · 09:07 KST 켜기** (`vm-power.yml` 의 `schedule`, 바꾸려면 cron 과 `case` 를 같이 수정). 수동으로 끈 날도 09:07 에 다시 켜진다
- 휴대폰: GitHub 앱 → RUNVEAL/Backend → Actions → **VM power** → Run workflow
- 클라우드 Claude(claude.ai/code): 환경 변수에 `GH_TOKEN` 이 있어야 한다. RUNVEAL/Backend 에 **Actions: Read and write** 만 준 fine-grained PAT 를 만들어 넣는다 (Azure 키는 필요 없다)
- stop 은 배포와 같은 `concurrency` 그룹이라 배포 도중에 꺼지지 않는다 (start·status 는 따로 돌아서 대기 중인 배포를 밀어내지 않는다)
- 배포 실패 시 롤백은 jar 와 `compose.yml`·`Caddyfile` 을 함께 되돌린다. 남은 임시 SSH 규칙(`gha-ssh-*`)은 다음 배포가 지운다

## 코드 배포 / 롤백 (수동)

```bash
bash deploy/release.sh                    # HEAD 빌드(테스트 포함) → 업로드 → 재기동 → smoke (실패 시 자동 롤백)
bash deploy/release.sh --rollback <sha>   # 이미 올린 jar 로 (releases/ 에 남아 있음)
```

리허설(11/10): 배포 35초 · 롤백 15초 · 다시 배포 35초, 매번 smoke 통과.

## 지도 교체

```bash
# 1) 번들 만들기 (graphDir = generateSegments 산출: 그래프 + segments.bin + manifest·index·segments-*.geojson)
bash deploy/pack-map.sh <graphDir> <areasDir> <adminDir> <srtmDir> <YYYY-MM-DD>
# 2) 올리기 (앱용 파일 먼저 공개) → 전환
bash deploy/push-map.sh build/maps/runveal-map-<ver>.tar.gz --activate
# 롤백: 이전 버전으로 전환
bash deploy/push-map.sh --activate-existing <이전 ver>
```

- 같은 버전은 덮어쓰지 않는다 (수정 = 새 버전)
- `areas/` 가 없으면 서버가 뜨지 않는다 (경로 정보 이름). `admin/` 이 없으면 `/match` 의 `sigunguCodes` 가 빈 배열
- 우선님 번들(`server-bundle.zip` + `cdn-pack.zip`)은 두 zip 을 한 폴더에 풀면 `graphDir` 이다
- 앱은 `/meta` 의 `mapVersions.current` 로 지도 버전을 안다 → 전환 전까지 새 버전 파일은 공개돼 있어도 아무도 안 받는다
- ponytail: 서버는 지도 버전 1개만 지원. 전환하면 옛 버전 앱은 409 → 지도를 새로 받는다. 2개 동시 지원은 별도 작업

## 운영 명령

```bash
source deploy/target.env; S="ssh -i $SSH_KEY $SSH_USER@$HOST"
$S 'cd /srv/runveal && sudo docker compose ps'
$S 'cd /srv/runveal && sudo docker compose logs api --tail 100'
# 로그에 좌표·IP 가 없는지 (0 이어야 함)
$S 'cd /srv/runveal && sudo docker compose logs --no-color | grep -cE "37\.5552|126\.9236|remote_ip|client_ip"'
```

| 상황 | 명령 |
| --- | --- |
| 안 쓸 때 크레딧 아끼기 | `vm-power` 워크플로 (위) 또는 `az vm deallocate -g runveal-rg -n runveal-api` |
| 내 IP 가 바뀌어 SSH 안 됨 | `az network nsg rule update -g runveal-rg --nsg-name runveal-apiNSG -n allow-ssh-me --source-address-prefixes $(curl -s https://api.ipify.org)/32` |
| 사양 올리기 | `az vm deallocate …` → `az vm resize -g runveal-rg -n runveal-api --size Standard_B4s_v2` → `az vm start …` |
| 도메인 바꾸기 | DNS A 레코드 → VM IP, `.env` 의 `SITE_ADDRESS` 수정 → `sudo docker compose up -d caddy` |

## 비용 (Azure for Students $100 / 12개월)

| 항목 | 월 대략 |
| --- | --- |
| `Standard_B2s_v2` 서울, 24시간 | 약 $76 |
| 디스크 30GB StandardSSD + 고정 IP | 약 $6 |

24시간 켜두면 약 5주. 자동 스케줄(하루 17시간, 월 약 $60)이면 약 7주. 안 쓰는 날은 `stop`. B 시리즈는 버스트형이라 CPU 크레딧을 다 쓰면 느려진다 (부하 테스트를 길게 돌릴 때 주의).

## 아직 없는 것

- 지도 버전 2개 동시 지원, 기동 시 해시 검증·워밍업 (업로드 때 `SHA256SUMS` 검증으로 대신)
- 회원 DB 백업 (회원 데이터가 생기면 `pg_dump` cron 또는 디스크 스냅샷)
- 무중단 배포 (서버 1대라 배포 중 약 30초 끊김. 앱은 503·연결 실패를 대기열로 재시도)
