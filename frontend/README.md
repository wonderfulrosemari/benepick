# benepick-frontend

계좌/카드 추천 UI (React + Vite + TypeScript)입니다.

## 실행

```bash
cd frontend
npm install
npm run dev
```

기본 백엔드 주소는 `http://localhost:8080`이며, 변경하려면:

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

## 현재 주요 기능

- 계좌/카드 입력 탭 분리
  - 계좌 탭: 운용 기간, 우대조건 감수, 급여이체 가능, 해외 이용 빈도, 계좌 성격 필터
  - 카드 탭: 카드 우선순위, 연회비 허용 범위, 전월실적 달성 자신도, 해외 결제 빈도, 혜택 카테고리
- 계좌/카드 필터 분리 전송
  - 제출 시 `accountCategories`, `cardCategories`를 각각 전송해 백엔드에서 분리 반영
- 추천 결과 카드
  - 상품 요약/핵심 설명/상세 필드 표시
  - 공식 계좌/카드 페이지 이동 버튼
  - 월 예상 이득(기대/최소/최대) 툴팁 표시
  - 클릭수/클릭률 표시
- 최소 기능 UI 정리
  - 운영용 동기화 패널/결과 불러오기 패널 제거
  - 입력 화면의 대표 기준/반영 신호 텍스트 제거

## 환경변수

- `VITE_API_BASE_URL`: 백엔드 API 주소
- `VITE_BASE_PATH`: GitHub Pages 배포 경로 (`/` 또는 `/<repo>/`)
- `VITE_SHOW_SYNC_PANEL`: 운영 동기화 패널 노출 여부 (`false` 권장)

## 주요 파일

- `frontend/src/App.tsx`: 입력 폼/결과 렌더링
- `frontend/src/components/ProductCard.tsx`: 상품 카드, 상세 정보, 월 예상 이득 툴팁
- `frontend/src/lib/api.ts`: 백엔드 API 호출
- `frontend/src/types/recommendation.ts`: API DTO 타입
- `frontend/src/styles.css`: 전체 스타일

