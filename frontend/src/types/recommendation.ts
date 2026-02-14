export type Priority = "cashback" | "savings" | "travel" | "starter" | "salary" | "annualfee";
export type AccountPriority = "savings" | "salary" | "starter" | "travel" | "cashback";
export type CardPriority = "cashback" | "annualfee" | "travel" | "starter" | "savings";
export type TravelLevel = "none" | "sometimes" | "often";

export type SimulateRecommendationRequest = {
  age: number;
  income: number;
  monthlySpend: number;
  priority: Priority;
  accountPriority: AccountPriority;
  cardPriority: CardPriority;
  salaryTransfer: "yes" | "no";
  travelLevel: TravelLevel;
  categories: string[];
  accountCategories?: string[];
  cardCategories?: string[];
};

export type RecommendationDetailField = {
  label: string;
  value: string;
  link: boolean;
};

export type RecommendationBundleBenefitComponent = {
  key: string;
  label: string;
  condition: string;
  amountWonPerMonth: number;
  applied: boolean;
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
  minExpectedMonthlyBenefit: number;
  expectedMonthlyBenefit: number;
  maxExpectedMonthlyBenefit: number;
  estimateMethod: string;
  benefitComponents: RecommendationBundleBenefitComponent[];
  detailFields: RecommendationDetailField[];
};

export type RecommendationBundle = {
  rank: number;
  title: string;
  accountProductId: string;
  accountLabel: string;
  cardProductId: string;
  cardLabel: string;
  minExtraMonthlyBenefit: number;
  expectedExtraMonthlyBenefit: number;
  maxExtraMonthlyBenefit: number;
  accountExpectedExtraMonthlyBenefit: number;
  cardExpectedExtraMonthlyBenefit: number;
  synergyExtraMonthlyBenefit: number;
  estimateMethod: string;
  benefitComponents: RecommendationBundleBenefitComponent[];
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

export type CatalogSyncTargetStatusResponse = {
  source: string;
  lastResult: string;
  lastTrigger: string;
  lastRunAt: string | null;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  lastMessage: string;
  lastFetched: number | null;
  lastUpserted: number | null;
  lastDeactivated: number | null;
  lastSkipped: number | null;
  consecutiveFailureCount: number;
};

export type CatalogSyncStatusResponse = {
  generatedAt: string;
  finlife: CatalogSyncTargetStatusResponse;
  cards: CatalogSyncTargetStatusResponse;
};

export type FinlifeSyncResponse = {
  fetchedProducts: number;
  upsertedProducts: number;
  deactivatedProducts: number;
  skippedProducts: number;
};

export type CardExternalSyncResponse = {
  fetched: number;
  upserted: number;
  deactivated: number;
  skipped: number;
};
