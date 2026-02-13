# recommend-frontend

계좌/카드 추천 웹 프론트엔드(React + Vite + TypeScript)입니다.

## 시작

```bash
npm install
npm run dev
```

## 주요 파일

- `src/App.tsx`: 입력 폼 + 추천 결과 화면
- `src/lib/recommend.ts`: 추천 점수 계산 로직
- `src/data/products.ts`: 샘플 상품 데이터
- `src/styles.css`: 반응형 UI 스타일

## 비고

현재 데이터는 데모용 샘플입니다. 실제 서비스에서는 백엔드(Spring Boot) API 연동으로 대체하면 됩니다.
