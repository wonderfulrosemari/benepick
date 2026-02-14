# Benepick Release Notes

## v0.1.0 (2026-02-13)

### 초기 릴리즈

- 백엔드 추천/리다이렉트 API 구현
- 카탈로그 동기화 API 구현
- 추천 UI/결과 화면 구현

---

## v0.2.0 (2026-02-15)

### 데이터/동기화

- FinLife 계좌 동기화 + 공공데이터 카드 다중 소스 연동
- 동기화 상태 API 추가
  - 마지막 성공/실패 시각, 건수, 실패 사유

### 추천 엔진

- 계좌/카드 카테고리 신호 분리 반영
  - `accountCategories`, `cardCategories`
- 가중치 기반 점수 체계 정리 (환경변수 기반 튜닝)
- 월 예상 이득(기대/최소/최대) 및 근거 컴포넌트 제공

### 프론트엔드 UX

- 계좌/카드 입력 탭 분리
- 필터 UI 아이콘 카드형 개선
- 추천 카드 가독성 개선 (상세 텍스트/문단 렌더링)
- 월 예상 이득 툴팁(호버) 제공
- 공식 상품 페이지 이동 강화

### 최소 기능 중심 UI 정리

- 결과 불러오기/공유 패널 제거
- 데이터 상태/동기화 패널 제거
- 입력 화면의 대표 기준/반영 신호 텍스트 제거

### 보안/운영

- 로컬/서버 비밀키 파일 분리
  - `backend/.env/secrets.local.properties`
  - `backend/.env/secrets.server.properties`
- 비밀키 파일 Git 제외 규칙 강화
- 배포 설정 추가
  - `render.yaml` (Render 백엔드)
  - GitHub Pages Actions 워크플로우
  - `DEPLOYMENT.md` 배포 가이드
- 헬스체크 노출 (`/actuator/health`)

