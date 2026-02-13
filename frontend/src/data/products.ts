export type AccountProduct = {
  id: string;
  provider: string;
  name: string;
  kind: "입출금" | "저축" | "외화";
  tags: string[];
  summary: string;
};

export type CardProduct = {
  id: string;
  provider: string;
  name: string;
  annualFee: string;
  tags: string[];
  categories: string[];
  summary: string;
};

export const accountProducts: AccountProduct[] = [
  {
    id: "acc_salary",
    provider: "한빛은행",
    name: "급여 플렉스 통장",
    kind: "입출금",
    tags: ["salary", "cashback", "daily"],
    summary: "급여이체 시 수수료 면제 + 자동이체 캐시백"
  },
  {
    id: "acc_savings",
    provider: "가온저축은행",
    name: "모으기 하이세이브",
    kind: "저축",
    tags: ["savings", "goal", "auto"],
    summary: "목표 저축 기반 우대금리 + 자동이체 연동"
  },
  {
    id: "acc_young",
    provider: "나래은행",
    name: "청년 스타트 통장",
    kind: "입출금",
    tags: ["starter", "young", "low-fee"],
    summary: "만 34세 이하 우대 + 수수료 부담 완화"
  },
  {
    id: "acc_travel",
    provider: "도담은행",
    name: "트래블 패스 외화통장",
    kind: "외화",
    tags: ["travel", "global", "fx"],
    summary: "해외결제 환전 우대 + 외화 자동충전"
  }
];

export const cardProducts: CardProduct[] = [
  {
    id: "card_life",
    provider: "온카드",
    name: "라이프 캐시백 카드",
    annualFee: "국내전용 1.2만원",
    tags: ["cashback", "daily"],
    categories: ["grocery", "transport", "dining"],
    summary: "장보기·교통·외식 중심 캐시백"
  },
  {
    id: "card_online",
    provider: "핀페이카드",
    name: "온라인 맥스 카드",
    annualFee: "국내전용 1.0만원",
    tags: ["cashback", "online"],
    categories: ["online", "subscription", "cafe"],
    summary: "온라인쇼핑·구독·간편결제 특화"
  },
  {
    id: "card_travel",
    provider: "루프카드",
    name: "에어마일 트래블 카드",
    annualFee: "국내외겸용 2.8만원",
    tags: ["travel", "mileage"],
    categories: ["online"],
    summary: "해외결제 적립 + 공항 라운지"
  },
  {
    id: "card_starter",
    provider: "비기너카드",
    name: "스타트 제로 카드",
    annualFee: "국내전용 없음",
    tags: ["starter", "no-fee"],
    categories: ["online", "grocery", "transport"],
    summary: "연회비 부담 없이 기본 적립"
  }
];
