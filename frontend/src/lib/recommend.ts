import { AccountProduct, CardProduct } from "../data/products";

export type Priority = "cashback" | "savings" | "travel" | "starter";
export type TravelLevel = "none" | "sometimes" | "often";

export type UserProfile = {
  age: number;
  income: number;
  monthlySpend: number;
  priority: Priority;
  salaryTransfer: "yes" | "no";
  travelLevel: TravelLevel;
  categories: string[];
};

export type Ranked<T> = {
  product: T;
  score: number;
  reasons: string[];
};

function hasTag(tags: string[], target: string): boolean {
  return tags.includes(target);
}

function countIntersection(source: string[], target: string[]): number {
  const set = new Set(source);
  return target.filter((item) => set.has(item)).length;
}

export function rankAccounts(
  products: AccountProduct[],
  profile: UserProfile
): Ranked<AccountProduct>[] {
  return products
    .map((product) => {
      let score = 45;
      const reasons: string[] = [];

      if (profile.salaryTransfer === "yes" && hasTag(product.tags, "salary")) {
        score += 30;
        reasons.push("급여이체 우대와 일치");
      }

      if (profile.priority === "savings" && hasTag(product.tags, "savings")) {
        score += 35;
        reasons.push("저축/금리 우선순위와 일치");
      }

      if (profile.priority === "starter" && hasTag(product.tags, "starter")) {
        score += 24;
        reasons.push("초보자용 성격과 일치");
      }

      if (profile.priority === "travel" && hasTag(product.tags, "travel")) {
        score += 20;
        reasons.push("여행/해외결제 선호 반영");
      }

      if (profile.travelLevel === "often" && hasTag(product.tags, "global")) {
        score += 28;
        reasons.push("해외 사용 빈도 높음");
      }

      if (profile.age <= 34 && hasTag(product.tags, "young")) {
        score += 20;
        reasons.push("연령 우대 구간 해당");
      }

      if (profile.monthlySpend >= 100 && hasTag(product.tags, "daily")) {
        score += 10;
        reasons.push("생활비 지출 패턴 반영");
      }

      return {
        product,
        score: Math.max(0, Math.round(score)),
        reasons: reasons.slice(0, 3)
      };
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, 3);
}

export function rankCards(
  products: CardProduct[],
  profile: UserProfile
): Ranked<CardProduct>[] {
  return products
    .map((product) => {
      let score = 45;
      const reasons: string[] = [];

      const hit = countIntersection(profile.categories, product.categories);
      score += hit * 9;
      if (hit > 0) {
        reasons.push(`소비 카테고리 ${hit}개 일치`);
      }

      if (profile.priority === "cashback" && hasTag(product.tags, "cashback")) {
        score += 24;
        reasons.push("캐시백 우선순위 반영");
      }

      if (profile.priority === "travel" && hasTag(product.tags, "travel")) {
        score += 22;
        reasons.push("여행 우선순위 반영");
      }

      if (profile.priority === "starter" && hasTag(product.tags, "starter")) {
        score += 24;
        reasons.push("연회비 부담 최소 선호 반영");
      }

      if (profile.travelLevel === "often" && hasTag(product.tags, "travel")) {
        score += 28;
        reasons.push("해외 결제 빈도 높음");
      }

      if (profile.monthlySpend >= 80 && hasTag(product.tags, "daily")) {
        score += 10;
        reasons.push("월 실적 충족 가능성 높음");
      }

      return {
        product,
        score: Math.max(0, Math.round(score)),
        reasons: reasons.slice(0, 3)
      };
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, 3);
}
