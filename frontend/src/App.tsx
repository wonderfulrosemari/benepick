import { useEffect, useMemo, useState } from 'react';
import ProductCard from './components/ProductCard';
import {
    getCatalogSummary,
    getRecommendationAnalytics,
    getRecommendationRun,
    redirectRecommendation,
    simulateRecommendations,
} from './lib/api';
import {
    AccountPriority,
    CardPriority,
    CatalogSummaryResponse,
    Priority,
    RecommendationAnalyticsResponse,
    RecommendationItem,
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

const accountPriorityLabel: Record<AccountPriority, string> = {
    savings: '금리/저축',
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

type ProductTab = 'account' | 'card';

function App() {
    const [profile, setProfile] = useState<SimulateRecommendationRequest>(initialProfile);
    const [activeTab, setActiveTab] = useState<ProductTab>('account');
    const [annualFeeFirst, setAnnualFeeFirst] = useState(false);
    const [accountFilters, setAccountFilters] = useState<string[]>(['savings', 'salary']);
    const [cardFilters, setCardFilters] = useState<string[]>(['online', 'grocery', 'subscription']);
    const [result, setResult] = useState<RecommendationRunResponse | null>(null);
    const [analytics, setAnalytics] = useState<RecommendationAnalyticsResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [redirectingKey, setRedirectingKey] = useState<string | null>(null);
    const [runLookupId, setRunLookupId] = useState('');
    const [loadingRun, setLoadingRun] = useState(false);
    const [catalogSummary, setCatalogSummary] = useState<CatalogSummaryResponse | null>(null);
    const [catalogLoading, setCatalogLoading] = useState(false);
    const [copied, setCopied] = useState(false);

    const effectiveCardPriority = annualFeeFirst ? 'annualfee' : profile.cardPriority;

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

    const loadAnalytics = async (runId: string) => {
        try {
            const response = await getRecommendationAnalytics(runId);
            setAnalytics(response);
        } catch {
            setAnalytics(null);
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
        } catch (e) {
            setError(e instanceof Error ? e.message : '추천 결과 조회에 실패했습니다.');
        } finally {
            setLoadingRun(false);
        }
    };

    useEffect(() => {
        void loadCatalog();

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
                cardPriority: effectiveCardPriority,
                priority: toLegacyPriority(profile.accountPriority, effectiveCardPriority),
                categories: mergedCategories,
                accountCategories: accountFilters,
                cardCategories: cardFilters,
            });
            setResult(response);
            setRunLookupId(response.runId);
            writeRunIdToUrl(response.runId);
            await loadAnalytics(response.runId);
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
            cardPriorityLabel[effectiveCardPriority],
            annualFeeFirst ? '연회비' : '',
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
        effectiveCardPriority,
        annualFeeFirst,
        profile.salaryTransfer,
        profile.travelLevel,
    ]);

    const clickStatsByProduct = useMemo(() => {
        const stats = new Map<string, { clickCount: number; clickRatePercent: number }>();
        if (!analytics) {
            return stats;
        }

        const totalRedirects = Math.max(analytics.totalRedirects, 0);
        for (const item of analytics.topClickedProducts) {
            const key = `${item.productType}:${item.productId}`;
            const clickRatePercent = totalRedirects > 0
                ? Math.round((item.clickCount / totalRedirects) * 1000) / 10
                : 0;
            stats.set(key, {
                clickCount: item.clickCount,
                clickRatePercent,
            });
        }

        return stats;
    }, [analytics]);

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

                <div className="workspace-layout">
                    <section className="panel fade-in delay-1 input-panel">
                        <h2>입력 조건</h2>
                        <div className="product-tab-toggle" role="tablist" aria-label="추천 종류 선택">
                            <button
                                type="button"
                                role="tab"
                                aria-selected={activeTab === 'account'}
                                className={activeTab === 'account' ? 'tab-button is-active' : 'tab-button'}
                                onClick={() => setActiveTab('account')}
                            >
                                계좌 선택
                            </button>
                            <button
                                type="button"
                                role="tab"
                                aria-selected={activeTab === 'card'}
                                className={activeTab === 'card' ? 'tab-button is-active' : 'tab-button'}
                                onClick={() => setActiveTab('card')}
                            >
                                카드 선택
                            </button>
                        </div>

                        <form className="form-grid" onSubmit={onSubmit}>
                            {activeTab === 'account' ? (
                                <>
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
                                        월 생활비/지출 (만원)
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
                                            onChange={(e) => {
                                                const accountPriority = e.target.value as AccountPriority;
                                                setProfile((prev) => ({
                                                    ...prev,
                                                    accountPriority,
                                                    priority: toLegacyPriority(accountPriority, effectiveCardPriority),
                                                }));
                                            }}
                                        >
                                            {Object.entries(accountPriorityLabel).map(([key, value]) => (
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
                                </>
                            ) : (
                                <>
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
                                        카드 추천 기준
                                        <select
                                            value={profile.cardPriority}
                                            onChange={(e) => {
                                                const cardPriority = e.target.value as CardPriority;
                                                setProfile((prev) => ({
                                                    ...prev,
                                                    cardPriority,
                                                    priority: toLegacyPriority(prev.accountPriority, cardPriority),
                                                }));
                                            }}
                                            disabled={annualFeeFirst}
                                        >
                                            {Object.entries(cardPriorityLabel).map(([key, value]) => (
                                                <option key={key} value={key}>
                                                    {value}
                                                </option>
                                            ))}
                                        </select>
                                    </label>

                                    <label>
                                        연회비 우선도
                                        <select
                                            value={annualFeeFirst ? 'high' : 'normal'}
                                            onChange={(e) => setAnnualFeeFirst(e.target.value === 'high')}
                                        >
                                            <option value="normal">일반</option>
                                            <option value="high">높음 (연회비 절감 우선)</option>
                                        </select>
                                    </label>

                                    <label>
                                        해외 결제 사용 빈도
                                        <select
                                            value={profile.travelLevel}
                                            onChange={(e) =>
                                                setProfile((prev) => ({
                                                    ...prev,
                                                    travelLevel: e.target
                                                        .value as SimulateRecommendationRequest['travelLevel'],
                                                }))
                                            }
                                        >
                                            <option value="none">거의 없음</option>
                                            <option value="sometimes">가끔</option>
                                            <option value="often">자주</option>
                                        </select>
                                    </label>

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
                                </>
                            )}

                            <button type="submit" className="button-primary" disabled={loading}>
                                {loading ? '추천 계산 중...' : '추천 계산'}
                            </button>
                        </form>
                    </section>

                    <section className="results fade-in delay-2">
                        {error ? <p className="error-banner">{error}</p> : null}
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
                                <button
                                    type="submit"
                                    className="ghost-button"
                                    disabled={loadingRun || !runLookupId.trim()}
                                >
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
                        </div>

                        {!result ? (
                            <div className="empty-state panel">추천 계산을 실행하면 결과가 표시됩니다.</div>
                        ) : (
                            <div className="result-grid single">
                                {activeTab === 'account' ? (
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
                                                        clickCount={analytics ? (clickStatsByProduct.get(key)?.clickCount ?? 0) : undefined}
                                                        clickRatePercent={analytics ? (clickStatsByProduct.get(key)?.clickRatePercent ?? 0) : undefined}
                                                        highlightKeywords={highlightKeywords}
                                                        actionLabel={resolveActionLabel(item)}
                                                        actionLoading={redirectingKey === key}
                                                        onAction={() => handleRedirect(item)}
                                                    />
                                                );
                                            })}
                                        </ul>
                                    </article>
                                ) : (
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
                                                        clickCount={analytics ? (clickStatsByProduct.get(key)?.clickCount ?? 0) : undefined}
                                                        clickRatePercent={analytics ? (clickStatsByProduct.get(key)?.clickRatePercent ?? 0) : undefined}
                                                        highlightKeywords={highlightKeywords}
                                                        actionLabel={resolveActionLabel(item)}
                                                        actionLoading={redirectingKey === key}
                                                        onAction={() => handleRedirect(item)}
                                                    />
                                                );
                                            })}
                                        </ul>
                                    </article>
                                )}
                            </div>
                        )}
                    </section>
                </div>

                <section className="panel status-strip fade-in delay-2 bottom-status">
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
