# Benepick Project Status

최종 업데이트: 2026-02-15

## 1) 현재 상태

개발 기능은 사실상 완료되었고, 현재는 배포/운영 전환 단계입니다.

- 추천 API/화면: 동작 완료
- 실데이터 동기화: 동작 완료 (FinLife + 공공데이터)
- 공식 페이지 이동: 동작 완료 (오버라이드/기관별 처리 포함)
- 배포 파일: 준비 완료 (Render + GitHub Pages)

## 2) 구현 완료 항목

### 2-1. 데이터 수집/동기화

- FinLife 계좌 데이터 동기화 API
  - `POST /api/catalog/sync/finlife`
- 카드 외부 데이터 동기화 API
  - `POST /api/catalog/sync/cards/external`
- 공공데이터 다중 소스 모드
  - KDB, 우체국, 금융위 통계
- 동기화 상태 API
  - `GET /api/catalog/sync/status`
  - 마지막 성공/실패 시각, 건수, 실패 사유 추적

### 2-2. 추천 엔진

- 계좌/카드 추천 신호 분리 반영
  - `accountCategories`, `cardCategories`
- 가중치 기반 점수 계산 체계
  - 환경변수로 조정 가능 (`REC_SCORE_*`)
- 월 예상 이득 산출
  - 기대/최소/최대 금액
  - 항목별 근거 컴포넌트
- 공식 링크 보강
  - `product-url-overrides.properties`로 상위 추천 딥링크 고정 가능

### 2-3. 프론트엔드 UX

- 계좌/카드 탭 분리 입력
- 필터를 카드형(아이콘) UI로 개선
- 추천 카드 상세 정보 가독성 개선
- 월 예상 이득 툴팁(호버 표시)
- 불필요 운영 UI 제거
  - 결과 불러오기/공유 패널 제거
  - 데이터 상태/동기화 패널 제거
  - 대표 기준/반영 신호 텍스트 제거

### 2-4. 보안/설정

- 비밀키 로딩 경로 분리
  - `backend/.env/secrets.local.properties`
  - `backend/.env/secrets.server.properties`
- `.gitignore`로 비밀키 파일 커밋 차단
- 예시 파일(`*.example.properties`)만 버전관리

## 3) 배포 준비 상태

### 3-1. 백엔드(Render)

- `render.yaml` 추가 완료
- `PORT` 환경변수 대응 완료
- `/actuator/health` 헬스체크 대응 완료

### 3-2. 프론트(GitHub Pages)

- GitHub Actions 배포 워크플로우 추가 완료
  - `.github/workflows/deploy-frontend-pages.yml`
- Vite base path/백엔드 URL 환경변수 대응 완료

## 4) 운영 체크포인트

- 필수 환경변수 입력
  - DB, JWT, Google, FinLife, 공공데이터 서비스키
- CORS 최소화
  - `FRONTEND_ORIGIN`을 실제 도메인만 허용
- 동기화 정책
  - 일일 호출 제한 고려해 스케줄 off + 수동 동기화 권장

## 5) 남은 작업 (배포 직전/직후)

1. Render 서비스 생성 및 환경변수 입력
2. GitHub Pages Actions 시크릿 입력
3. 프론트/백엔드 연결 스모크 테스트
4. 운영 도메인/CORS 최종 고정

