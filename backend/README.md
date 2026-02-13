# benepick-backend

Spring Boot 기반 백엔드입니다.

## 실행

```bash
./gradlew bootRun
```

## 필수 환경 변수

- `GOOGLE_CLIENT_ID`
- `JWT_SECRET`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

## 선택 환경 변수

- `FINLIFE_AUTH_KEY`: 금융감독원 금융상품한눈에 API 키
- `CARD_EXTERNAL_SOURCE_URL`: 외부 카드 JSON 소스 URL 또는 파일 경로
  - 파일 예시: `/Users/jojinhyeok/IdeaProjects/recommend/backend/examples/card-external-sample.json`
  - URL 예시: `https://raw.githubusercontent.com/<owner>/<repo>/<branch>/backend/examples/card-external-sample.json`

## 인증 API

- `POST /api/auth/google`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

## 추천 API

- `POST /api/recommendations/simulate`
- `GET /api/recommendations/history`
- `GET /api/recommendations/{runId}`
- `GET /api/recommendations/{runId}/analytics`
- `POST /api/recommendations/{runId}/redirect`

## 카탈로그 API

- `GET /api/catalog/summary`
- `POST /api/catalog/sync/finlife`
- `POST /api/catalog/sync/cards/external`

`sync/finlife`는 금융상품한눈에 API(`FINLIFE_AUTH_KEY`)로 예금/적금 데이터를 동기화합니다.  
`sync/cards/external`은 외부 카드 JSON 소스(`CARD_EXTERNAL_SOURCE_URL`)를 동기화합니다.

## 운영 URL 연동 절차

1. 외부 카드 JSON을 고정 URL로 준비합니다.
2. 백엔드 환경변수 `CARD_EXTERNAL_SOURCE_URL`에 URL을 설정합니다.
3. 백엔드 재시작 후 아래 호출로 동기화합니다.

```bash
curl -s -X POST http://localhost:8080/api/catalog/sync/cards/external
curl -s http://localhost:8080/api/catalog/summary
```

## 혼합 데이터 전략

- 계좌 추천: 시드 데이터 + 금융감독원 동기화 데이터(`finlife:` prefix)
- 카드 추천: 시드 데이터 + 외부 카드 동기화 데이터(`external:` prefix)

추천 시에는 `active=true` 데이터만 사용합니다.

## 서비스 경계

Benepick은 추천 및 공식 사이트 이동(redirect)까지 제공하며,
실제 계좌 개설/이체/카드 발급 같은 은행 업무는 수행하지 않습니다.
