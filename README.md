# Benepick

사용자 입력 조건을 기반으로 계좌/카드 상품을 추천하고, 공식 상품 페이지로 안전하게 이동시키는 프로젝트입니다.

## 프로젝트 구성

- `frontend` : React + Vite + TypeScript UI
- `backend` : Spring Boot + JPA + PostgreSQL API

## 핵심 기능

- 실데이터 동기화
  - 계좌: 금융감독원 FinLife API
  - 카드: 공공데이터 다중 소스(KDB/우체국/금융위)
- 추천 엔진
  - 계좌/카드 입력 신호를 분리 반영
  - 가중치 기반 점수 + 월 예상 이득 산출
- 사용자 UX
  - 계좌/카드 탭 기반 입력 폼
  - 상품 상세/공식 링크 이동
  - 월 예상 이득 툴팁(호버)
- 운영/보안
  - 로컬/서버 비밀키 파일 분리
  - 배포 시 관리자성 동기화 UI 비노출

## 문서

- 전체 진행 요약: `PROJECT_STATUS.md`
- 배포 가이드: `DEPLOYMENT.md`
- 백엔드 상세: `backend/README.md`
- 프론트 상세: `frontend/README.md`
- 릴리즈 노트: `RELEASE_NOTES_v0.1.0.md`

