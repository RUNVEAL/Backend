# CLAUDE.md

개발 가이드·컨벤션은 **[AGENTS.md](./AGENTS.md)** 에 있다. 작업 전 반드시 읽고 따른다.

## 하네스: 백엔드 에이전트

**목표:** 백엔드 변경을 고신뢰로 리뷰하고, Java 도메인 모델을 캡슐화·책임 중심으로 리팩터링한다.

**호출 조건:**
- 백엔드 diff·PR·브랜치·working-tree 변경을 리뷰할 때 `review-backend-code` 스킬을 사용한다.
- Java 도메인 모델의 캡슐화·책임 배치를 리팩터링할 때 `refactor-backend-domain` 스킬을 사용한다.
- 하네스를 감사할 때 `audit-backend-harness`, 개선할 때 `evolve-backend-harness` 스킬을 사용한다. (`harness:harness`·`harness:evolve` 플러그인이 있으면 대신 써도 된다.)

**구성:** 스킬 원본(4개)은 `.agents/skills/`(Codex용) 한 곳에만 있고, `.claude/skills`는 그 폴더를 가리키는 심링크 하나다(Codex·Claude Code 공용, 파일은 한 벌 — 편집·추가는 `.agents/` 쪽만). `harness:*` 플러그인은 개인 설치라 저장소에 커밋되지 않으므로, Claude Code만 쓰는 팀원도 저장소만으로 4개를 전부 받는다.

## 운영 서버 (클라우드 Claude 포함)

- VM 켜기·끄기·상태: `gh workflow run vm-power.yml -R RUNVEAL/Backend -f action=start|stop|status` (Azure 키 불필요, `GH_TOKEN` 만 있으면 됨)
- 배포는 `main` 머지 시 자동 (`.github/workflows/deploy.yml`). 자세한 건 [deploy/README.md](deploy/README.md)
