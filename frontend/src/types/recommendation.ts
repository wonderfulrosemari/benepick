export type Priority = "cashback" | "savings" | "travel" | "starter";
export type TravelLevel = "none" | "sometimes" | "often";

export type SimulateRecommendationRequest = {
  age: number;
  income: number;
  monthlySpend: number;
  priority: Priority;
  salaryTransfer: "yes" | "no";
  travelLevel: TravelLevel;
  categories: string[];
};

export type RecommendationItem = {
  rank: number;
  productType: "ACCOUNT" | "CARD";
  productId: string;
  provider: string;
  name: string;
  summary: string;
  meta: string;
  score: number;
  reason: string;
};

export type RecommendationBundle = {
  rank: number;
  title: string;
  accountProductId: string;
  accountLabel: string;
  cardProductId: string;
  cardLabel: string;
  expectedExtraMonthlyBenefit: number;
  reason: string;
};

export type RecommendationRunResponse = {
  runId: string;
  priority: Priority;
  expectedNetMonthlyProfit: number;
  accounts: RecommendationItem[];
  cards: RecommendationItem[];
  bundles: RecommendationBundle[];
};

export type RecommendationRunHistoryItem = {
  runId: string;
  priority: Priority;
  expectedNetMonthlyProfit: number;
  redirectCount: number;
  createdAt: string;
};

export type RecommendationRedirectResponse = {
  url: string;
};

export type RecommendationClickStat = {
  productType: "ACCOUNT" | "CARD";
  productId: string;
  provider: string;
  name: string;
  rank: number;
  clickCount: number;
  lastClickedAt: string | null;
};

export type RecommendationCategoryStat = {
  categoryKey: string;
  categoryLabel: string;
  recommendedProducts: number;
  totalRedirects: number;
  uniqueClickedProducts: number;
  clickRatePercent: number;
  conversionRatePercent: number;
};

export type RecommendationAnalyticsResponse = {
  runId: string;
  totalRecommendationItems: number;
  totalRedirects: number;
  uniqueClickedProducts: number;
  uniqueClickRatePercent: number;
  topClickedProducts: RecommendationClickStat[];
  categoryStats: RecommendationCategoryStat[];
};

export type CatalogSummaryResponse = {
  totalAccounts: number;
  finlifeAccounts: number;
  totalCards: number;
  externalCards: number;
};
