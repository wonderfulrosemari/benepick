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

- 계좌/카드 필터 분리
  - 계좌 필터(계좌 전용): 저축/금리, 급여/주거래, 초보/간편, 외화/해외, 생활비 관리
  - 카드 필터(카드 전용): 온라인쇼핑, 장보기/마트, 교통, 외식, 카페, 구독
- 추천 결과 조회/공유
  - runId 기반 결과 재조회
  - 최근 추천 이력 목록
- 추천 클릭 분석
  - 총 클릭, 클릭된 상품 수, 상품 클릭 도달률
  - 카테고리별 추천수/클릭수/클릭률/전환률
- 월 예상 이득 툴팁
  - 호버 0.5초 후 표시
  - 월 기대 이득/최소·최대/적용 항목 표시
- 공식 페이지 이동
  - 추천 항목별 공식 계좌/카드 페이지 이동 API 연동

## 주요 파일

- `frontend/src/App.tsx`: 입력 폼, 결과/이력/분석 UI
- `frontend/src/components/ProductCard.tsx`: 상품 카드, 상세 정보, 월 예상 이득 툴팁
- `frontend/src/lib/api.ts`: 백엔드 API 호출
- `frontend/src/types/recommendation.ts`: API DTO 타입
- `frontend/src/styles.css`: 전체 스타일
