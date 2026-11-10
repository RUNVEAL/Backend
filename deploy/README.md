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

- Caddy 에 `log` 지시어를 넣지 않는다 → 접속 로그(IP·시각) 없음
- `api` 는 `-XX:-HeapDumpOnOutOfMemoryError` (힙 덤프 금지). 에러 추적 도구를 붙이면 본문 수집을 끈다
- `/livez` · `/readyz` · `/actuator/**` 는 외부에서 404 (Compose healthcheck 만 내부에서 쓴다)
- 확인: `python3 deploy/smoke.py https://<host> --spoof-check` + 로그 grep (아래)

## 처음 세팅 (한 번)

```bash
az login                          # Azure for Students 구독
bash deploy/azure-setup.sh        # RG · VM · 방화벽(80/443 전체, 22 내 IP만) · docker · .env
```

## 코드 배포 / 롤백

```bash
bash deploy/release.sh                    # HEAD 빌드(테스트 포함) → 업로드 → 재기동 → smoke
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
$S 'cd /srv/runveal && sudo docker compose logs --no-color | grep -cE "37\.5552|126\.9236"'
```

| 상황 | 명령 |
| --- | --- |
| 안 쓸 때 크레딧 아끼기 | `az vm deallocate -g runveal-rg -n runveal-api` (디스크·IP 요금만) |
| 다시 켜기 | `az vm start -g runveal-rg -n runveal-api` → `bash deploy/wait-healthy.sh` |
| 내 IP 가 바뀌어 SSH 안 됨 | `az network nsg rule update -g runveal-rg --nsg-name runveal-apiNSG -n allow-ssh-me --source-address-prefixes $(curl -s https://api.ipify.org)/32` |
| 사양 올리기 | `az vm deallocate …` → `az vm resize -g runveal-rg -n runveal-api --size Standard_B4s_v2` → `az vm start …` |
| 도메인 바꾸기 | DNS A 레코드 → VM IP, `.env` 의 `SITE_ADDRESS` 수정 → `sudo docker compose up -d caddy` |

## 비용 (Azure for Students $100 / 12개월)

| 항목 | 월 대략 |
| --- | --- |
| `Standard_B2s_v2` 서울, 24시간 | 약 $76 |
| 디스크 30GB StandardSSD + 고정 IP | 약 $6 |

24시간 켜두면 약 5~6주. 안 쓰는 날은 `deallocate`. B 시리즈는 버스트형이라 CPU 크레딧을 다 쓰면 느려진다 (부하 테스트를 길게 돌릴 때 주의).

## 아직 없는 것

- 지도 버전 2개 동시 지원, 기동 시 해시 검증·워밍업 (업로드 때 `SHA256SUMS` 검증으로 대신)
- 회원 DB 백업 (회원 데이터가 생기면 `pg_dump` cron 또는 디스크 스냅샷)
- CI/CD 자동 배포 (지금은 `release.sh` 수동)
