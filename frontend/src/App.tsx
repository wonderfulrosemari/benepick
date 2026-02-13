import { useEffect, useMemo, useState } from "react";
import ProductCard from "./components/ProductCard";
import {
  getCatalogSummary,
  getRecommendationAnalytics,
  getRecommendationHistory,
  getRecommendationRun,
  redirectRecommendation,
  simulateRecommendations
} from "./lib/api";
import {
  CatalogSummaryResponse,
  Priority,
  RecommendationAnalyticsResponse,
  RecommendationItem,
  RecommendationRunHistoryItem,
  RecommendationRunResponse,
  SimulateRecommendationRequest
} from "./types/recommendation";

const categories = [
  { key: "online", label: "온라인쇼핑" },
  { key: "grocery", label: "장보기/마트" },
  { key: "transport", label: "교통" },
  { key: "dining", label: "외식" },
  { key: "cafe", label: "카페" },
  { key: "subscription", label: "구독" }
];

const priorityLabel: Record<Priority, string> = {
  cashback: "생활 할인/캐시백",
  savings: "저축/금리",
  travel: "여행/해외결제",
  starter: "초보자/연회비 최소"
};

const initialProfile: SimulateRecommendationRequest = {
  age: 29,
  income: 300,
  monthlySpend: 120,
  priority: "cashback",
  salaryTransfer: "yes",
  travelLevel: "none",
  categories: ["online", "grocery", "subscription"]
};

function App() {
  const [profile, setProfile] = useState<SimulateRecommendationRequest>(initialProfile);
  const [result, setResult] = useState<RecommendationRunResponse | null>(null);
  const [analytics, setAnalytics] = useState<RecommendationAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [redirectingKey, setRedirectingKey] = useState<string | null>(null);
  const [runLookupId, setRunLookupId] = useState("");
  const [loadingRun, setLoadingRun] = useState(false);
  const [runHistory, setRunHistory] = useState<RecommendationRunHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [catalogSummary, setCatalogSummary] = useState<CatalogSummaryResponse | null>(null);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  const activePriority: Priority = result?.priority ?? profile.priority;

  const runShareUrl = useMemo(() => {
    if (!result) {
      return "";
    }

    const url = new URL(window.location.href);
    url.searchParams.set("runId", result.runId);
    return url.toString();
  }, [result]);

  const writeRunIdToUrl = (runId: string) => {
    const url = new URL(window.location.href);
    url.searchParams.set("runId", runId);
    window.history.replaceState({}, "", url.toString());
  };

  const loadCatalog = async () => {
    setCatalogLoading(true);
    try {
      const summary = await getCatalogSummary();
      setCatalogSummary(summary);
    } catch {
      // keep UI usable even if summary fails
    } finally {
      setCatalogLoading(false);
    }
  };

  const loadHistory = async () => {
    setHistoryLoading(true);
    try {
      const rows = await getRecommendationHistory(8);
      setRunHistory(rows);
    } catch {
      setRunHistory([]);
    } finally {
      setHistoryLoading(false);
    }
  };

  const loadAnalytics = async (runId: string) => {
    setAnalyticsLoading(true);
    try {
      const response = await getRecommendationAnalytics(runId);
      setAnalytics(response);
    } catch {
      setAnalytics(null);
    } finally {
      setAnalyticsLoading(false);
    }
  };

  const loadRun = async (runId: string) => {
    const normalizedRunId = runId.trim();
    if (!normalizedRunId) {
      return;
    }

    setLoadingRun(true);
    setError(null);

    try {
      const response = await getRecommendationRun(normalizedRunId);
      setResult(response);
      setRunLookupId(response.runId);
      writeRunIdToUrl(response.runId);
      await loadAnalytics(response.runId);
      await loadHistory();
    } catch (e) {
      setError(e instanceof Error ? e.message : "추천 결과 조회에 실패했습니다.");
    } finally {
      setLoadingRun(false);
    }
  };

  useEffect(() => {
    void loadCatalog();
    void loadHistory();

    const initialRunId = new URLSearchParams(window.location.search).get("runId");
    if (initialRunId) {
      setRunLookupId(initialRunId);
      void loadRun(initialRunId);
    }
  }, []);

  const onSubmit: React.FormEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const response = await simulateRecommendations(profile);
      setResult(response);
      setRunLookupId(response.runId);
      writeRunIdToUrl(response.runId);
      await loadAnalytics(response.runId);
      await loadHistory();
    } catch (e) {
      setError(e instanceof Error ? e.message : "추천 요청에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const toggleCategory = (value: string) => {
    setProfile((prev) => {
      const has = prev.categories.includes(value);
      return {
        ...prev,
        categories: has
          ? prev.categories.filter((item) => item !== value)
          : [...prev.categories, value]
      };
    });
  };

  const handleRedirect = async (item: RecommendationItem) => {
    if (!result) {
      return;
    }

    const key = `${item.productType}:${item.productId}`;
    setRedirectingKey(key);
    setError(null);

    try {
      const response = await redirectRecommendation(result.runId, item.productType, item.productId);
      window.open(response.url, "_blank", "noopener,noreferrer");
      await loadAnalytics(result.runId);
      await loadHistory();
    } catch (e) {
      setError(e instanceof Error ? e.message : "사이트 이동 요청에 실패했습니다.");
    } finally {
      setRedirectingKey(null);
    }
  };

  const handleBundleRedirect = async (productType: "ACCOUNT" | "CARD", productId: string) => {
    if (!result) {
      return;
    }

    const key = `bundle:${productType}:${productId}`;
    setRedirectingKey(key);
    setError(null);

    try {
      const response = await redirectRecommendation(result.runId, productType, productId);
      window.open(response.url, "_blank", "noopener,noreferrer");
      await loadAnalytics(result.runId);
      await loadHistory();
    } catch (e) {
      setError(e instanceof Error ? e.message : "패키지 이동 요청에 실패했습니다.");
    } finally {
      setRedirectingKey(null);
    }
  };

  const copyShareUrl = async () => {
    if (!runShareUrl) {
      return;
    }

    try {
      await navigator.clipboard.writeText(runShareUrl);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      setError("클립보드 복사에 실패했습니다. 주소창 URL을 직접 복사해 주세요.");
    }
  };

  const formatClickedAt = (value: string | null) => {
    if (!value) {
      return "-";
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return date.toLocaleString("ko-KR", { hour12: false });
  };

  const formatHistoryTime = (value: string) => {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString("ko-KR", { hour12: false });
  };

  const dataStatusLabel = catalogSummary
    ? catalogSummary.finlifeAccounts > 0
      ? "실데이터 일부 동기화됨"
      : "현재 시드 데이터 사용 중"
    : "데이터 상태 확인 중";

  return (
    <div className="app">
      <div className="bg-grid" aria-hidden="true" />

      <header className="container site-header">
        <p className="brand">Benepick</p>
      </header>

      <main className="container">
        <section className="hero fade-in">
          <h1>카드·계좌 추천 후 공식 사이트까지 안전하게 연결</h1>
          <p>
            Benepick은 은행 업무를 대행하지 않고, 사용자 조건에 맞는 상품 추천과
            공식 사이트 이동만 제공합니다.
          </p>
        </section>

        <section className="panel status-strip fade-in delay-1">
          <p className="status-title">데이터 상태</p>
          <p className="status-value">{dataStatusLabel}</p>
          <p className="status-meta">
            계좌 {catalogSummary?.totalAccounts ?? "-"}개 · 실데이터 계좌 {catalogSummary?.finlifeAccounts ?? "-"}개 ·
            카드 {catalogSummary?.totalCards ?? "-"}개 · 외부 카드 {catalogSummary?.externalCards ?? "-"}개
          </p>
          <button type="button" className="ghost-button" onClick={() => void loadCatalog()} disabled={catalogLoading}>
            {catalogLoading ? "갱신 중..." : "상태 다시 확인"}
          </button>
        </section>

        <section className="panel fade-in delay-1">
          <h2>입력 조건</h2>
          <form className="form-grid" onSubmit={onSubmit}>
            <label>
              나이
              <input
                type="number"
                min={19}
                max={100}
                value={profile.age}
                onChange={(e) =>
                  setProfile((prev) => ({ ...prev, age: Number(e.target.value) || 0 }))
                }
                required
              />
            </label>

            <label>
              월 소득 (만원)
              <input
                type="number"
                min={0}
                value={profile.income}
                onChange={(e) =>
                  setProfile((prev) => ({ ...prev, income: Number(e.target.value) || 0 }))
                }
                required
              />
            </label>

            <label>
              월 카드 사용액 (만원)
              <input
                type="number"
                min={0}
                value={profile.monthlySpend}
                onChange={(e) =>
                  setProfile((prev) => ({
                    ...prev,
                    monthlySpend: Number(e.target.value) || 0
                  }))
                }
                required
              />
            </label>

            <label>
              우선순위
              <select
                value={profile.priority}
                onChange={(e) =>
                  setProfile((prev) => ({
                    ...prev,
                    priority: e.target.value as SimulateRecommendationRequest["priority"]
                  }))
                }
              >
                {Object.entries(priorityLabel).map(([key, value]) => (
                  <option key={key} value={key}>
                    {value}
                  </option>
                ))}
              </select>
            </label>

            <label>
              급여이체 가능 여부
              <select
                value={profile.salaryTransfer}
                onChange={(e) =>
                  setProfile((prev) => ({
                    ...prev,
                    salaryTransfer: e.target.value as SimulateRecommendationRequest["salaryTransfer"]
                  }))
                }
              >
                <option value="yes">가능</option>
                <option value="no">어려움</option>
              </select>
            </label>

            <label>
              해외 결제 사용 빈도
              <select
                value={profile.travelLevel}
                onChange={(e) =>
                  setProfile((prev) => ({
                    ...prev,
                    travelLevel: e.target.value as SimulateRecommendationRequest["travelLevel"]
                  }))
                }
              >
                <option value="none">거의 없음</option>
                <option value="sometimes">가끔</option>
                <option value="often">자주</option>
              </select>
            </label>

            <fieldset className="category-group">
              <legend>주요 소비 카테고리</legend>
              {categories.map((category) => (
                <label key={category.key}>
                  <input
                    type="checkbox"
                    checked={profile.categories.includes(category.key)}
                    onChange={() => toggleCategory(category.key)}
                  />
                  {category.label}
                </label>
              ))}
            </fieldset>

            <button type="submit" className="button-primary" disabled={loading}>
              {loading ? "추천 계산 중..." : "추천 계산"}
            </button>
          </form>
        </section>

        {error ? <p className="error-banner">{error}</p> : null}

        <section className="results fade-in delay-2">
          <div className="result-header">
            <h2>추천 결과</h2>
            <p>
              선택된 우선순위: <strong>{priorityLabel[activePriority]}</strong>
            </p>
            <p>
              예상 월 순이익: <strong>{result ? `${result.expectedNetMonthlyProfit.toLocaleString()}원` : "-"}</strong>
            </p>
          </div>

          <div className="panel run-tools">
            <h3>결과 불러오기 / 공유</h3>
            <form
              className="inline-form"
              onSubmit={(event) => {
                event.preventDefault();
                void loadRun(runLookupId);
              }}
            >
              <input
                type="text"
                placeholder="runId 입력 후 이전 결과 불러오기"
                value={runLookupId}
                onChange={(event) => setRunLookupId(event.target.value)}
              />
              <button type="submit" className="ghost-button" disabled={loadingRun || !runLookupId.trim()}>
                {loadingRun ? "불러오는 중..." : "결과 불러오기"}
              </button>
            </form>

            {result ? (
              <div className="share-row">
                <p>
                  현재 runId: <code>{result.runId}</code>
                </p>
                <button type="button" className="ghost-button" onClick={() => void copyShareUrl()}>
                  공유 링크 복사
                </button>
                {copied ? <span className="copy-note">복사됨</span> : null}
              </div>
            ) : null}

            <div className="history-wrap">
              <div className="history-head">
                <p>최근 추천 이력</p>
                <button type="button" className="ghost-button" onClick={() => void loadHistory()}>
                  {historyLoading ? "갱신 중..." : "새로고침"}
                </button>
              </div>

              {historyLoading ? (
                <p className="history-empty">불러오는 중...</p>
              ) : runHistory.length > 0 ? (
                <ul className="history-list">
                  {runHistory.map((row) => (
                    <li key={row.runId} className="history-item">
                      <button type="button" className="history-runid" onClick={() => void loadRun(row.runId)}>
                        {row.runId}
                      </button>
                      <p className="history-meta">
                        {priorityLabel[row.priority]} · 예상 {row.expectedNetMonthlyProfit.toLocaleString()}원 · 클릭 {row.redirectCount}회 · {formatHistoryTime(row.createdAt)}
                      </p>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="history-empty">아직 저장된 추천 이력이 없습니다.</p>
              )}
            </div>
          </div>

          {result ? (
            <article className="panel analytics-panel">
              <h3>추천 클릭 분석</h3>
              {analyticsLoading ? (
                <p className="analytics-loading">분석 불러오는 중...</p>
              ) : analytics ? (
                <>
                  <div className="analytics-kpis">
                    <div className="analytics-kpi">
                      <p className="analytics-kpi-label">총 클릭</p>
                      <p className="analytics-kpi-value">{analytics.totalRedirects}</p>
                    </div>
                    <div className="analytics-kpi">
                      <p className="analytics-kpi-label">클릭된 상품 수</p>
                      <p className="analytics-kpi-value">{analytics.uniqueClickedProducts}</p>
                    </div>
                    <div className="analytics-kpi">
                      <p className="analytics-kpi-label">상품 클릭 도달률</p>
                      <p className="analytics-kpi-value">{analytics.uniqueClickRatePercent}%</p>
                    </div>
                  </div>

                  {analytics.categoryStats.length > 0 ? (
                    <div className="analytics-category-table-wrap">
                      <table className="analytics-category-table">
                        <thead>
                          <tr>
                            <th>카테고리</th>
                            <th>추천수</th>
                            <th>클릭수</th>
                            <th>클릭률</th>
                            <th>전환률</th>
                          </tr>
                        </thead>
                        <tbody>
                          {analytics.categoryStats.map((row) => (
                            <tr key={`category-${row.categoryKey}`}>
                              <td>{row.categoryLabel}</td>
                              <td>{row.recommendedProducts}</td>
                              <td>{row.totalRedirects}</td>
                              <td>{row.clickRatePercent}%</td>
                              <td>{row.conversionRatePercent}%</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : null}

                  {analytics.topClickedProducts.length > 0 ? (
                    <ul className="analytics-list">
                      {analytics.topClickedProducts.map((item) => (
                        <li key={`analytics-${item.productType}-${item.productId}`} className="analytics-item">
                          <p className="analytics-item-title">
                            {item.productType} #{item.rank} · {item.provider} · {item.name}
                          </p>
                          <p className="analytics-item-meta">
                            클릭 {item.clickCount}회 · 마지막 클릭 {formatClickedAt(item.lastClickedAt)}
                          </p>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="analytics-empty">아직 클릭 데이터가 없습니다.</p>
                  )}
                </>
              ) : (
                <p className="analytics-empty">분석 데이터를 불러오지 못했습니다.</p>
              )}
            </article>
          ) : null}

          {result?.bundles.length ? (
            <article className="panel bundle-panel">
              <h3>계좌 + 카드 패키지 추천</h3>
              <ul className="bundle-list">
                {result.bundles.map((bundle) => (
                  <li
                    key={`bundle-${bundle.rank}-${bundle.accountProductId}-${bundle.cardProductId}`}
                    className="bundle-card"
                  >
                    <div className="card-top">
                      <p className="rank">BUNDLE {bundle.rank}</p>
                      <p className="score">+{bundle.expectedExtraMonthlyBenefit.toLocaleString()}원/월</p>
                    </div>
                    <h4>{bundle.title}</h4>
                    <p className="summary">계좌: {bundle.accountLabel}</p>
                    <p className="summary">카드: {bundle.cardLabel}</p>
                    <p className="reason">{bundle.reason}</p>
                    <div className="bundle-actions">
                      <button
                        type="button"
                        className="ghost-button"
                        onClick={() => void handleBundleRedirect("ACCOUNT", bundle.accountProductId)}
                        disabled={redirectingKey === `bundle:ACCOUNT:${bundle.accountProductId}`}
                      >
                        계좌 페이지
                      </button>
                      <button
                        type="button"
                        className="ghost-button"
                        onClick={() => void handleBundleRedirect("CARD", bundle.cardProductId)}
                        disabled={redirectingKey === `bundle:CARD:${bundle.cardProductId}`}
                      >
                        카드 페이지
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            </article>
          ) : null}

          {!result ? (
            <div className="empty-state panel">추천 계산을 실행하면 결과가 표시됩니다.</div>
          ) : (
            <div className="result-grid">
              <article className="panel">
                <h3>추천 계좌</h3>
                <ul className="product-list">
                  {result.accounts.map((item) => {
                    const key = `${item.productType}:${item.productId}`;
                    return (
                      <ProductCard
                        key={key}
                        rank={item.rank}
                        score={item.score}
                        title={`${item.provider} · ${item.name}`}
                        summary={item.summary}
                        meta={item.meta}
                        reason={item.reason}
                        actionLabel="공식 계좌 페이지 이동"
                        actionLoading={redirectingKey === key}
                        onAction={() => handleRedirect(item)}
                      />
                    );
                  })}
                </ul>
              </article>

              <article className="panel">
                <h3>추천 카드</h3>
                <ul className="product-list">
                  {result.cards.map((item) => {
                    const key = `${item.productType}:${item.productId}`;
                    return (
                      <ProductCard
                        key={key}
                        rank={item.rank}
                        score={item.score}
                        title={`${item.provider} · ${item.name}`}
                        summary={item.summary}
                        meta={item.meta}
                        reason={item.reason}
                        actionLabel="공식 카드 페이지 이동"
                        actionLoading={redirectingKey === key}
                        onAction={() => handleRedirect(item)}
                      />
                    );
                  })}
                </ul>
              </article>
            </div>
          )}
        </section>
      </main>

      <footer className="site-footer">
        <p>
          본 서비스는 추천과 공식 사이트 연결만 제공합니다. 실제 가입 전 최신 약관,
          우대금리, 수수료 조건을 반드시 확인하세요.
        </p>
      </footer>
    </div>
  );
}

export default App;
