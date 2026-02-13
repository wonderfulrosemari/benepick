# benepick-backend

Spring Boot 기반 백엔드입니다.

## 실행

```bash
./gradlew bootRun
```

## 로컬 비밀키 파일(권장)

`backend/src/main/resources/application.yml`에서 아래 파일을 자동 로드하도록 설정했습니다.

- `backend/config/secrets.properties`
- `backend/config/secrets-local.properties`

실행 절차:

```bash
cd backend
cp config/secrets.example.properties config/secrets.properties
# secrets.properties에 실제 키 입력
./gradlew bootRun
```

주의:
- `config/secrets.properties`는 `.gitignore`로 제외되어 Git에 올라가지 않습니다.
- `config/product-url-overrides.properties`도 `.gitignore`로 제외되어 로컬 URL 매핑을 안전하게 관리할 수 있습니다.
- IntelliJ Run Configuration 환경변수를 계속 써도 되지만, 배포/운영은 Secret Manager 또는 서버 환경변수로 관리하는 것을 권장합니다.

## 필수 환경 변수

- `GOOGLE_CLIENT_ID`
- `JWT_SECRET`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

## 선택 환경 변수

- `FINLIFE_AUTH_KEY`: 금융감독원 금융상품한눈에 API 키
- 카드 외부 동기화 모드
  - `CARD_EXTERNAL_MODE=source` (기본): JSON 파일/URL
  - `CARD_EXTERNAL_MODE=public-data`: 공공데이터 API 단일 소스
  - `CARD_EXTERNAL_MODE=public-data-all`: 공공데이터 API 다중 소스(KDB/우체국/금융위)

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

`simulate` 요청 필드(주요):
- `categories`: 하위 호환용 통합 카테고리
- `accountCategories`: 계좌 점수 계산 전용 카테고리
- `cardCategories`: 카드 점수 계산 전용 카테고리

## 카탈로그 API

- `GET /api/catalog/summary`
- `POST /api/catalog/sync/finlife`
- `POST /api/catalog/sync/cards/external`

`sync/finlife`는 금융상품한눈에 API(`FINLIFE_AUTH_KEY`)로 예금/적금 데이터를 동기화합니다.  
`sync/cards/external`은 카드 외부 소스를 동기화합니다.

## 카드 외부 소스 설정

### 1) JSON 소스 모드 (`source`)

```env
CARD_EXTERNAL_MODE=source
CARD_EXTERNAL_SOURCE_URL=/Users/jojinhyeok/IdeaProjects/recommend/backend/examples/card-external-sample.json
```

### 2) 공공데이터 단일 소스 모드 (`public-data`)

```env
CARD_EXTERNAL_MODE=public-data
CARD_PUBLIC_DATA_URL=https://apis.data.go.kr/<service>/<endpoint>
CARD_PUBLIC_DATA_SERVICE_KEY=발급받은키
CARD_PUBLIC_DATA_ITEMS_PATH=response.body.items.item
```

### 3) 공공데이터 다중 소스 모드 (`public-data-all`)

```env
CARD_EXTERNAL_MODE=public-data-all
CARD_PUBLIC_ALL_SERVICE_KEY=발급받은키
CARD_PUBLIC_ALL_INCLUDE_KDB=true
CARD_PUBLIC_ALL_INCLUDE_KRPOST=true
CARD_PUBLIC_ALL_INCLUDE_FINANCE_STATS=true
CARD_PUBLIC_ALL_KDB_MAX_PAGES=20
CARD_PUBLIC_ALL_KRPOST_MAX_PAGES=20
CARD_PUBLIC_ALL_FIN_STATS_MAX_PAGES=20
```

기본 내장 소스:
- 한국산업은행 카드상품 정보 (`B190030`)
- 우체국 체크카드상품 정보 (`1721301`)
- 금융위원회 신용카드사 통계 (`1160100`)

참고:
- 금융위 통계 데이터는 카드 카탈로그에는 저장되지만 `stat-only` 태그로 저장되며 추천 랭킹 계산에서는 제외됩니다.
- XML 응답 API도 자동 파싱(JSON/XML)을 지원합니다.

## 상품 상세 URL 오버라이드

추천 클릭 시 회사 메인 페이지 대신 상품 전용 상세 페이지로 보내고 싶다면
`config/product-url-overrides.properties`에 오버라이드를 추가하세요.

지원 키 형식:
- `productKey=url`
- `PRODUCT_TYPE|providerName|productName=url` (`PRODUCT_TYPE`: `ACCOUNT`/`CARD`)

예시:

```properties
# exact productKey
finlife:saving:0010001:wr0001f=https://spot.wooribank.com/pot/Dream?...

# top recommendation deep-link guarantee
ACCOUNT|우리은행|우리SUPER주거래적금=https://spot.wooribank.com/pot/Dream?...
CARD|KB국민카드|온라인 맥스=https://card.kbcard.com/CXPRICAC0076.cms
```

매칭 우선순위:
1. `productKey`
2. `PRODUCT_TYPE|provider|product`
3. `provider|product`
4. `PRODUCT_TYPE|product`

적용 방식:
- 추천/리다이렉트 시점에 파일을 즉시 읽어 반영됩니다.
- 카탈로그 DB에도 반영하려면 재동기화(`finlife`, `cards/external`)를 추가로 실행하세요.
- 공식 URL은 오버라이드 파일 기준으로 즉시 반영됩니다.
- 상품별 딥링크가 없는 경우에는 기관 메인/목록 URL로 이동할 수 있으므로, 상위 추천 상품은 오버라이드 등록을 권장합니다.

경로 변경이 필요하면 환경변수로 지정할 수 있습니다:

```env
CATALOG_PRODUCT_URL_OVERRIDES_PATH=./config/product-url-overrides.properties
```

## 동기화 확인

```bash
curl -s -X POST http://localhost:8080/api/catalog/sync/finlife
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

## 카탈로그 자동 동기화 스케줄

기본값은 다음과 같습니다.

- 앱 시작 직후 1회 자동 동기화
- 매일 03:30 (Asia/Seoul) 자동 동기화

환경변수로 제어:

```env
CATALOG_SYNC_ENABLED=true
CATALOG_SYNC_STARTUP_ENABLED=true
CATALOG_SYNC_SCHEDULED_ENABLED=true
CATALOG_SYNC_FINLIFE_ENABLED=true
CATALOG_SYNC_CARDS_ENABLED=true
CATALOG_SYNC_CRON=0 30 3 * * *
CATALOG_SYNC_ZONE=Asia/Seoul
```

예시:
- 시작 시 자동 동기화만 끄기: `CATALOG_SYNC_STARTUP_ENABLED=false`
- 계좌 동기화만 끄기: `CATALOG_SYNC_FINLIFE_ENABLED=false`
- 정기 스케줄만 끄기: `CATALOG_SYNC_SCHEDULED_ENABLED=false`

## 추천 품질 튜닝

추천 점수는 `recommendation.scoring` 설정값으로 분리되어 있어 환경변수로 조정할 수 있습니다.

- 프리셋: `REC_SCORING_PROFILE` (`balanced` | `conservative` | `aggressive`)
- 계좌 가중치: `REC_SCORE_ACCOUNT_*`
- 카드 가중치: `REC_SCORE_CARD_*`

주요 개선 사항:
- 카드/계좌 카테고리 매핑 정규화(영문/한글/요약 키워드 기반)
- 우선순위별 가중치 분리(저축형/소비형/여행형/초보자형)
- 추천 사유 문구에 실제 데이터 필드 반영
  - 계좌: 요약의 금리(최고/기본), 계좌종류, 우대 신호
  - 카드: 연회비 텍스트, 카테고리 일치, 혜택 요약 하이라이트

## 동기화 상태 API

운영 점검용으로 마지막 동기화 성공/실패 상태를 조회할 수 있습니다.

- `GET /api/catalog/sync/status`
  - 대상별(`FINLIFE`, `CARDS`) 마지막 실행 시각
  - 마지막 성공 시각 / 실패 시각
  - 마지막 처리 건수(`fetched`, `upserted`, `deactivated`, `skipped`)
  - 마지막 실패 사유(`lastMessage`)와 연속 실패 횟수

예시:

```bash
curl -s http://localhost:8080/api/catalog/sync/status
```

## 추천 품질 측정 루프

클릭/전환 로그를 기반으로 카테고리별 CTR/CVR를 주기적으로 집계하고,
가중치 조정 근거(추천/클릭/전환 수치 + 제안 액션)를 스냅샷으로 저장합니다.

- 자동 실행: `recommendation.quality.*` 설정
- 수동 실행: `POST /api/recommendations/quality/recompute`
- 최신 조회: `GET /api/recommendations/quality/latest`

제안 액션 규칙:
- `UP`: CTR/CVR가 높은 카테고리 (가중치 상향 제안)
- `DOWN`: CTR/CVR가 낮은 카테고리 (가중치 하향 제안)
- `HOLD`: 표본 부족 또는 중립 구간
