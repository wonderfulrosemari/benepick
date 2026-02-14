# Deployment Guide (Low Cost)

이 프로젝트는 아래 무료/저비용 조합으로 운영할 수 있습니다.

- 프론트: GitHub Pages (무료)
- 백엔드: Render Web Service (Free)
- DB: Neon Postgres (Free) 또는 Supabase Postgres (Free)

## 1) Backend (Render)

`render.yaml`이 이미 포함되어 있습니다. Render 대시보드에서 `Blueprint`로 가져오거나, 직접 Web Service를 생성해도 됩니다.

필수 환경변수:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `GOOGLE_CLIENT_ID`
- `FRONTEND_ORIGIN`
- `FINLIFE_AUTH_KEY`
- `CARD_PUBLIC_ALL_SERVICE_KEY`

권장값(일일 API 호출 제한 보호):

- `CATALOG_SYNC_STARTUP_ENABLED=false`
- `CATALOG_SYNC_SCHEDULED_ENABLED=false`
- 운영자가 필요할 때만 수동 동기화

헬스체크:

- `GET /actuator/health`

## 2) Database (Neon / Supabase)

### Neon

1. 프로젝트 생성
2. Postgres 연결 문자열 발급
3. Render `DB_URL/DB_USERNAME/DB_PASSWORD`에 입력

### Supabase

1. 프로젝트 생성
2. Database connection string 확인
3. Render 환경변수에 입력

> 선택 기준
> - DB만 필요: Neon이 단순
> - Auth/Storage/Realtime도 함께 필요: Supabase

## 3) Frontend (GitHub Pages)

워크플로우 파일:

- `.github/workflows/deploy-frontend-pages.yml`

GitHub Repository Settings:

1. `Settings > Pages > Build and deployment`
2. Source: `GitHub Actions`
3. `Settings > Secrets and variables > Actions`에 아래 추가
   - `VITE_API_BASE_URL`: Render 백엔드 URL (예: `https://benepick-backend.onrender.com`)
   - `VITE_BASE_PATH`: 커스텀 도메인이면 `/`, 프로젝트 페이지면 `/<repo-name>/`

배포 후 CORS:

- Render의 `FRONTEND_ORIGIN`을 Pages 도메인으로 설정
  - 예: `https://<username>.github.io`
  - 또는 커스텀 도메인

## 4) Smoke Test

배포 직후 최소 검증:

1. 프론트 접속 확인
2. 추천 계산 API 정상 응답
3. 공식 페이지 이동 클릭 정상
4. 백엔드 헬스체크 `200`

