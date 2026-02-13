import {
  CatalogSummaryResponse,
  RecommendationAnalyticsResponse,
  RecommendationRedirectResponse,
  RecommendationRunHistoryItem,
  RecommendationRunResponse,
  SimulateRecommendationRequest
} from "../types/recommendation";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function requestJson<T>(path: string, options: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(options.headers ?? {})
    }
  });

  if (!response.ok) {
    let errorMessage = `요청 실패 (${response.status})`;
    try {
      const body = await response.json();
      if (typeof body?.message === "string" && body.message.length > 0) {
        errorMessage = body.message;
      }
    } catch {
      // ignore json parsing failure
    }
    throw new Error(errorMessage);
  }

  return response.json() as Promise<T>;
}

export async function simulateRecommendations(
  payload: SimulateRecommendationRequest
): Promise<RecommendationRunResponse> {
  return requestJson<RecommendationRunResponse>("/api/recommendations/simulate", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function getRecommendationHistory(limit = 10): Promise<RecommendationRunHistoryItem[]> {
  return requestJson<RecommendationRunHistoryItem[]>(`/api/recommendations/history?limit=${limit}`, {
    method: "GET"
  });
}

export async function getRecommendationRun(runId: string): Promise<RecommendationRunResponse> {
  return requestJson<RecommendationRunResponse>(`/api/recommendations/${runId}`, {
    method: "GET"
  });
}

export async function getRecommendationAnalytics(runId: string): Promise<RecommendationAnalyticsResponse> {
  return requestJson<RecommendationAnalyticsResponse>(`/api/recommendations/${runId}/analytics`, {
    method: "GET"
  });
}

export async function redirectRecommendation(
  runId: string,
  productType: string,
  productId: string
): Promise<RecommendationRedirectResponse> {
  return requestJson<RecommendationRedirectResponse>(
    `/api/recommendations/${runId}/redirect`,
    {
      method: "POST",
      body: JSON.stringify({ productType, productId })
    }
  );
}

export async function getCatalogSummary(): Promise<CatalogSummaryResponse> {
  return requestJson<CatalogSummaryResponse>("/api/catalog/summary", {
    method: "GET"
  });
}
