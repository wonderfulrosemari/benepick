import { useEffect, useMemo, useState } from 'react';
import ProductCard from './components/ProductCard';
import {
    getCatalogSummary,
    getRecommendationAnalytics,
    getRecommendationHistory,
    getRecommendationRun,
    redirectRecommendation,
    simulateRecommendations,
} from './lib/api';
import {
    CatalogSummaryResponse,
    AccountPriority,
    CardPriority,
    Priority,
    RecommendationAnalyticsResponse,
    RecommendationItem,
    RecommendationRunHistoryItem,
    RecommendationRunResponse,
    SimulateRecommendationRequest,
} from './types/recommendation';

const accountCategoryOptions = [
    { key: 'savings', label: '저축/금리' },
    { key: 'salary', label: '급여/주거래' },
    { key: 'starter', label: '초보/간편' },
    { key: 'travel', label: '외화/해외' },
    { key: 'cashback', label: '생활비 관리' },
];

const cardCategoryOptions = [
    { key: 'online', label: '온라인쇼핑' },
    { key: 'grocery', label: '장보기/마트' },
    { key: 'transport', label: '교통' },
    { key: 'dining', label: '외식' },
    { key: 'cafe', label: '카페' },
    { key: 'subscription', label: '구독' },
];

const priorityLabel: Record<Priority, string> = {
    cashback: '생활 할인/캐시백',
    savings: '저축/금리',
    travel: '여행/해외결제',
    starter: '초보자/연회비 최소',
    salary: '급여이체/주거래',
    annualfee: '연회비 절감',
};

const accountPriorityLabel: Record<AccountPriority, string> = {
    savings: '금리/저축 중심',
    salary: '급여이체/주거래',
    starter: '간편/초보자',
    travel: '외화/해외 연계',
    cashback: '생활비 관리',
};

const cardPriorityLabel: Record<CardPriority, string> = {
    cashback: '생활 할인/캐시백',
    annualfee: '연회비 절감',
    travel: '여행/해외결제',
    starter: '초보자/무난형',
    savings: '고정비 절감',
};

const splitKoreanKeywords = (value: string): string[] =>
    value
        .split(/[\s/(),·]+/)
        .map((token) => token.trim())
        .filter((token) => token.length >= 2);

const toLegacyPriority = (accountPriority: AccountPriority, cardPriority: CardPriority): Priority => {
    if (cardPriority === 'annualfee') {
        return 'starter';
    }
    if (cardPriority === 'travel') {
        return 'travel';
    }
    if (accountPriority === 'savings' || accountPriority === 'salary') {
        return 'savings';
    }
    return 'cashback';
};

const initialProfile: SimulateRecommendationRequest = {
    age: 29,
    income: 300,
    monthlySpend: 120,
    priority: 'cashback',
    accountPriority: 'savings',
    cardPriority: 'cashback',
    salaryTransfer: 'yes',
    travelLevel: 'none',
    categories: ['online', 'grocery', 'subscription'],
};

function App() {
    const [profile, setProfile] = useState<SimulateRecommendationRequest>(initialProfile);
    const [accountFilters, setAccountFilters] = useState<string[]>(['savings', 'salary']);
    const [cardFilters, setCardFilters] = useState<string[]>(['online', 'grocery', 'subscription']);
    const [result, setResult] = useState<RecommendationRunResponse | null>(null);
    const [analytics, setAnalytics] = useState<RecommendationAnalyticsResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [redirectingKey, setRedirectingKey] = useState<string | null>(null);
    const [runLookupId, setRunLookupId] = useState('');
    const [loadingRun, setLoadingRun] = useState(false);
    const [runHistory, setRunHistory] = useState<RecommendationRunHistoryItem[]>([]);
    const [historyLoading, setHistoryLoading] = useState(false);
    const [analyticsLoading, setAnalyticsLoading] = useState(false);
    const [catalogSummary, setCatalogSummary] = useState<CatalogSummaryResponse | null>(null);
    const [catalogLoading, setCatalogLoading] = useState(false);
    const [copied, setCopied] = useState(false);

    const runShareUrl = useMemo(() => {
        if (!result) {
            return '';
        }

        const url = new URL(window.location.href);
        url.searchParams.set('runId', result.runId);
        return url.toString();
    }, [result]);

    const writeRunIdToUrl = (runId: string) => {
        const url = new URL(window.location.href);
        url.searchParams.set('runId', runId);
        window.history.replaceState({}, '', url.toString());
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
            setError(e instanceof Error ? e.message : '추천 결과 조회에 실패했습니다.');
        } finally {
            setLoadingRun(false);
        }
    };

    useEffect(() => {
        void loadCatalog();
        void loadHistory();

        const initialRunId = new URLSearchParams(window.location.search).get('runId');
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
            const mergedCategories = Array.from(new Set([...accountFilters, ...cardFilters]));
            const response = await simulateRecommendations({
                ...profile,
                categories: mergedCategories,
            });
            setResult(response);
            setRunLookupId(response.runId);
            writeRunIdToUrl(response.runId);
            await loadAnalytics(response.runId);
            await loadHistory();
        } catch (e) {
            setError(e instanceof Error ? e.message : '추천 요청에 실패했습니다.');
        } finally {
            setLoading(false);
        }
    };

    const toggleAccountFilter = (value: string) => {
        setAccountFilters((prev) =>
            prev.includes(value) ? prev.filter((item) => item !== value) : [...prev, value]
        );
    };

    const toggleCardFilter = (value: string) => {
        setCardFilters((prev) =>
            prev.includes(value) ? prev.filter((item) => item !== value) : [...prev, value]
        );
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
            window.open(response.url, '_blank', 'noopener,noreferrer');
            await loadAnalytics(result.runId);
            await loadHistory();
        } catch (e) {
            setError(e instanceof Error ? e.message : '사이트 이동 요청에 실패했습니다.');
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
            setError('클립보드 복사에 실패했습니다. 주소창 URL을 직접 복사해 주세요.');
        }
    };

    const formatClickedAt = (value: string | null) => {
        if (!value) {
            return '-';
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return date.toLocaleString('ko-KR', { hour12: false });
    };

    const formatHistoryTime = (value: string) => {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        return date.toLocaleString('ko-KR', { hour12: false });
    };

    const resolveActionLabel = (item: RecommendationItem) => {
        if (item.productType === 'ACCOUNT') {
            return '공식 계좌 페이지 이동';
        }

        return '공식 카드 페이지 이동';
    };

    const dataStatusLabel = catalogSummary
        ? catalogSummary.finlifeAccounts > 0
            ? '실데이터 일부 동기화됨'
            : '현재 시드 데이터 사용 중'
        : '데이터 상태 확인 중';

    const highlightKeywords = useMemo(() => {
        const selectedAccountLabels = accountCategoryOptions
            .filter((category) => accountFilters.includes(category.key))
            .map((category) => category.label);

        const selectedCardLabels = cardCategoryOptions
            .filter((category) => cardFilters.includes(category.key))
            .map((category) => category.label);

        const rawKeywords = [
            ...selectedAccountLabels,
            ...selectedCardLabels,
            accountPriorityLabel[profile.accountPriority],
            cardPriorityLabel[profile.cardPriority],
        ];

        if (profile.salaryTransfer === 'yes') {
            rawKeywords.push('급여이체', '급여');
        }

        if (profile.travelLevel !== 'none') {
            rawKeywords.push('여행', '해외');
        }

        return Array.from(new Set(rawKeywords.flatMap(splitKoreanKeywords)));
    }, [
        accountFilters,
        cardFilters,
        profile.accountPriority,
        profile.cardPriority,
        profile.salaryTransfer,
        profile.travelLevel,
    ]);

    return (
        <div className="app">
            <div className="bg-grid" aria-hidden="true" />

            <header className="container site-header">
                <p className="brand">Benepick</p>
            </header>

            <main className="container">
                <section className="hero fade-in">
                    <h1>카드·계좌 추천 후 공식 사이트까지 안전하게 연결</h1>
                    <p>은행 업무를 대행하지 않고, 사용자 조건에 맞는 상품 추천과 공식 사이트 이동만 제공합니다.</p>
                </section>

                <section className="panel status-strip fade-in delay-1">
                    <p className="status-title">데이터 상태</p>
                    <p className="status-value">{dataStatusLabel}</p>
                    <p className="status-meta">
                        계좌 {catalogSummary?.totalAccounts ?? '-'}개 · 실데이터 계좌{' '}
                        {catalogSummary?.finlifeAccounts ?? '-'}개 · 카드 {catalogSummary?.totalCards ?? '-'}개 · 외부
                        카드 {catalogSummary?.externalCards ?? '-'}개
                    </p>
                    <button
                        type="button"
                        className="ghost-button"
                        onClick={() => void loadCatalog()}
                        disabled={catalogLoading}
                    >
                        {catalogLoading ? '갱신 중...' : '상태 다시 확인'}
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
                                onChange={(e) => setProfile((prev) => ({ ...prev, age: Number(e.target.value) || 0 }))}
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
                                        monthlySpend: Number(e.target.value) || 0,
                                    }))
                                }
                                required
                            />
                        </label>

                        <label>
                            계좌 추천 기준
                            <select
                                value={profile.accountPriority}
                                onChange={(e) =>
                                    setProfile((prev) => {
                                        const accountPriority = e.target.value as AccountPriority;
                                        return {
                                            ...prev,
                                            accountPriority,
                                            priority: toLegacyPriority(accountPriority, prev.cardPriority),
                                        };
                                    })
                                }
                            >
                                {Object.entries(accountPriorityLabel).map(([key, value]) => (
                                    <option key={key} value={key}>
                                        {value}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label>
                            카드 추천 기준
                            <select
                                value={profile.cardPriority}
                                onChange={(e) =>
                                    setProfile((prev) => {
                                        const cardPriority = e.target.value as CardPriority;
                                        return {
                                            ...prev,
                                            cardPriority,
                                            priority: toLegacyPriority(prev.accountPriority, cardPriority),
                                        };
                                    })
                                }
                            >
                                {Object.entries(cardPriorityLabel).map(([key, value]) => (
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
                                        salaryTransfer: e.target
                                            .value as SimulateRecommendationRequest['salaryTransfer'],
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
                                        travelLevel: e.target.value as SimulateRecommendationRequest['travelLevel'],
                                    }))
                                }
                            >
                                <option value="none">거의 없음</option>
                                <option value="sometimes">가끔</option>
                                <option value="often">자주</option>
                            </select>
                        </label>

                        <fieldset className="category-group category-group-account">
                            <legend>계좌 필터 (계좌 전용)</legend>
                            <p className="category-help">저축/주거래/해외 등 계좌 추천에만 반영됩니다.</p>
                            {accountCategoryOptions.map((category) => (
                                <label key={category.key}>
                                    <input
                                        type="checkbox"
                                        checked={accountFilters.includes(category.key)}
                                        onChange={() => toggleAccountFilter(category.key)}
                                    />
                                    {category.label}
                                </label>
                            ))}
                        </fieldset>

                        <fieldset className="category-group category-group-card">
                            <legend>카드 필터 (카드 전용)</legend>
                            <p className="category-help">소비 카테고리 일치 점수는 카드 추천에만 반영됩니다.</p>
                            {cardCategoryOptions.map((category) => (
                                <label key={category.key}>
                                    <input
                                        type="checkbox"
                                        checked={cardFilters.includes(category.key)}
                                        onChange={() => toggleCardFilter(category.key)}
                                    />
                                    {category.label}
                                </label>
                            ))}
                        </fieldset>

                        <button type="submit" className="button-primary" disabled={loading}>
                            {loading ? '추천 계산 중...' : '추천 계산'}
                        </button>
                    </form>
                </section>

                {error ? <p className="error-banner">{error}</p> : null}

                <section className="results fade-in delay-2">
                    <div className="result-header">
                        <h2>추천 결과</h2>
                        <p>
                            입력 기준:{' '}
                            <strong>
                                계좌 {accountPriorityLabel[profile.accountPriority]} · 카드{' '}
                                {cardPriorityLabel[profile.cardPriority]}
                            </strong>
                        </p>
                        <p>
                            예상 월 순이익:{' '}
                            <strong>{result ? `${result.expectedNetMonthlyProfit.toLocaleString()}원` : '-'}</strong>
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
                                {loadingRun ? '불러오는 중...' : '결과 불러오기'}
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
                                    {historyLoading ? '갱신 중...' : '새로고침'}
                                </button>
                            </div>

                            {historyLoading ? (
                                <p className="history-empty">불러오는 중...</p>
                            ) : runHistory.length > 0 ? (
                                <ul className="history-list">
                                    {runHistory.map((row) => (
                                        <li key={row.runId} className="history-item">
                                            <button
                                                type="button"
                                                className="history-runid"
                                                onClick={() => void loadRun(row.runId)}
                                            >
                                                {row.runId}
                                            </button>
                                            <p className="history-meta">
                                                {priorityLabel[row.priority]} · 예상{' '}
                                                {row.expectedNetMonthlyProfit.toLocaleString()}원 · 클릭{' '}
                                                {row.redirectCount}회 · {formatHistoryTime(row.createdAt)}
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
                                                <li
                                                    key={`analytics-${item.productType}-${item.productId}`}
                                                    className="analytics-item"
                                                >
                                                    <p className="analytics-item-title">
                                                        {item.productType} #{item.rank} · {item.provider} · {item.name}
                                                    </p>
                                                    <p className="analytics-item-meta">
                                                        클릭 {item.clickCount}회 · 마지막 클릭{' '}
                                                        {formatClickedAt(item.lastClickedAt)}
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
                                                productType={item.productType}
                                                title={`${item.provider} · ${item.name}`}
                                                summary={item.summary}
                                                meta={item.meta}
                                                reason={item.reason}
                                                minExpectedMonthlyBenefit={item.minExpectedMonthlyBenefit}
                                                expectedMonthlyBenefit={item.expectedMonthlyBenefit}
                                                maxExpectedMonthlyBenefit={item.maxExpectedMonthlyBenefit}
                                                benefitComponents={item.benefitComponents}
                                                detailFields={item.detailFields}
                                                highlightKeywords={highlightKeywords}
                                                actionLabel={resolveActionLabel(item)}
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
                                                productType={item.productType}
                                                title={`${item.provider} · ${item.name}`}
                                                summary={item.summary}
                                                meta={item.meta}
                                                reason={item.reason}
                                                minExpectedMonthlyBenefit={item.minExpectedMonthlyBenefit}
                                                expectedMonthlyBenefit={item.expectedMonthlyBenefit}
                                                maxExpectedMonthlyBenefit={item.maxExpectedMonthlyBenefit}
                                                benefitComponents={item.benefitComponents}
                                                detailFields={item.detailFields}
                                                highlightKeywords={highlightKeywords}
                                                actionLabel={resolveActionLabel(item)}
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
                    본 서비스는 추천과 공식 사이트 연결만 제공합니다. 실제 가입 전 최신 약관, 우대금리, 수수료 조건을
                    반드시 확인하세요.
                </p>
            </footer>
        </div>
    );
}

export default App;
