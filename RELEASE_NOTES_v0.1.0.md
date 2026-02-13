# Benepick v0.1.0 Release Notes

출시일: 2026-02-13  
태그: `v0.1.0`

## 주요 변경사항

### 1) 백엔드 기능 확장 (Spring Boot)
- 추천 실행 API 추가
  - `POST /api/recommendations/simulate`
  - `GET /api/recommendations/history`
  - `GET /api/recommendations/{runId}`
  - `GET /api/recommendations/{runId}/analytics`
  - `POST /api/recommendations/{runId}/redirect`
- 카탈로그 동기화 API 추가
  - `GET /api/catalog/summary`
  - `POST /api/catalog/sync/finlife`
  - `POST /api/catalog/sync/cards/external`
- 데이터 전략
  - 계좌: 시드 + 금감원(금융상품한눈에) 동기화 데이터 혼합
  - 카드: 시드 + 외부 JSON 소스 동기화 데이터 혼합
- 인증 기본 구조 포함
  - Google 로그인/JWT/Refresh 토큰 관련 엔드포인트 및 보안 설정 추가

### 2) 프론트엔드 기능 확장 (React + Vite + TypeScript)
- 추천 입력 폼 및 결과 화면 구현
- 추천 결과 저장 이력(최근 runId) UI 추가
- 추천 클릭 분석(카테고리별 클릭률/전환률) 대시보드 UI 추가
- 백엔드 API 연동 구조(`src/lib/api.ts`) 정리

### 3) 정리 작업
- 프론트엔드 생성 산출물(`.tsbuildinfo`, 임시 d.ts/js) 제거
- `.gitignore` 보완

## 운영 설정 참고

- 필수 환경변수
  - `DB_URL`
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `JWT_SECRET`
  - `GOOGLE_CLIENT_ID`
- 선택 환경변수
  - `FINLIFE_AUTH_KEY`
  - `CARD_EXTERNAL_SOURCE_URL`

## 알려진 제한사항 (v0.1.0)
- 금감원 API 키 미설정 시 `sync/finlife` 호출은 `400` 반환
- 클릭/전환 통계는 실제 redirect 이벤트가 발생해야 수치가 누적됨
- Benepick은 추천 및 공식 사이트 이동까지 제공하며, 계좌 개설/발급 같은 은행 업무는 수행하지 않음

## 포함 커밋
- `692453d` feat(backend): add recommendation APIs and external catalog sync
- `497c9fb` feat(frontend): add recommendation dashboard and history UI
- `a3fa029` chore(frontend): remove generated TypeScript build artifacts
