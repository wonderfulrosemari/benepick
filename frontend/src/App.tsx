import { useEffect, useMemo, useState } from 'react';
import accountFilterSavingsIcon from './assets/account-filter/savings.svg';
import accountFilterSalaryIcon from './assets/account-filter/salary.svg';
import accountFilterStarterIcon from './assets/account-filter/starter.svg';
import accountFilterTravelIcon from './assets/account-filter/travel.svg';
import accountFilterCashbackIcon from './assets/account-filter/cashback.svg';
import cardFilterOnlineIcon from './assets/card-filter/online.svg';
import cardFilterGroceryIcon from './assets/card-filter/grocery.svg';
import cardFilterTransportIcon from './assets/card-filter/transport.svg';
import cardFilterDiningIcon from './assets/card-filter/dining.svg';
import cardFilterCafeIcon from './assets/card-filter/cafe.svg';
import cardFilterSubscriptionIcon from './assets/card-filter/subscription.svg';
import ProductCard from './components/ProductCard';
import {
    getRecommendationAnalytics,
    getRecommendationRun,
    redirectRecommendation,
    simulateRecommendations,
} from './lib/api';
import {
    AccountPriority,
    CardPriority,
    Priority,
    RecommendationAnalyticsResponse,
    RecommendationItem,
    RecommendationRunResponse,
    SimulateRecommendationRequest,
} from './types/recommendation';

const accountCategoryOptions = [
    { key: 'savings', label: '저축/금리', icon: accountFilterSavingsIcon },
    { key: 'salary', label: '급여/주거래', icon: accountFilterSalaryIcon },
    { key: 'starter', label: '초보/간편', icon: accountFilterStarterIcon },
    { key: 'travel', label: '외화/해외', icon: accountFilterTravelIcon },
    { key: 'cashback', label: '생활비 관리', icon: accountFilterCashbackIcon },
];

const cardCategoryOptions = [
    { key: 'online', label: '온라인쇼핑', icon: cardFilterOnlineIcon },
    { key: 'grocery', label: '장보기/마트', icon: cardFilterGroceryIcon },
    { key: 'transport', label: '교통', icon: cardFilterTransportIcon },
    { key: 'dining', label: '외식', icon: cardFilterDiningIcon },
    { key: 'cafe', label: '카페', icon: cardFilterCafeIcon },
    { key: 'subscription', label: '구독', icon: cardFilterSubscriptionIcon },
];

const accountPriorityLabel: Record<AccountPriority, string> = {
    savings: '금리/저축',
    salary: '급여이체/주거래',
    starter: '간편/초보자',
    travel: '외화/해외 연계',
    cashback: '생활비 관리',
};

const accountPriorityOrder: AccountPriority[] = ['savings', 'salary', 'starter', 'travel', 'cashback'];

const cardPriorityLabel: Record<CardPriority, string> = {
    cashback: '생활 할인/캐시백',
    annualfee: '연회비 절감',
    travel: '여행/해외결제',
    starter: '초보자/무난형',
    savings: '고정비 절감',
};

const accountTermOptions = [
    { key: 'short', label: '단기 유동성 (6개월 이하)' },
    { key: 'middle', label: '중기 균형 (6~12개월)' },
    { key: 'long', label: '장기 저축 (1년 이상)' },
] as const;

const accountConditionOptions = [
    { key: 'simple', label: '간단한 조건 선호' },
    { key: 'balanced', label: '보통 수준 조건 허용' },
    { key: 'active', label: '우대조건 적극 충족 가능' },
] as const;

const cardAnnualFeeBudgetOptions = [
    { key: 'free', label: '연회비 없음만' },
    { key: 'under10k', label: '1만원 이하' },
    { key: 'under20k', label: '2만원 이하' },
    { key: 'flexible', label: '연회비보다 혜택 우선' },
] as const;

const cardPerformanceOptions = [
    { key: 'light', label: '전월실적 낮게' },
    { key: 'balanced', label: '전월실적 보통' },
    { key: 'high', label: '전월실적 높아도 가능' },
] as const;

const splitKoreanKeywords = (value: string): string[] =>
    value
        .split(/[\s/(),·]+/)
        .map((token) => token.trim())
        .filter((token) => token.length >= 2);

const dedupe = (values: string[]) => Array.from(new Set(values));

const accountWeightHints = {
    accountTerm:
        '운용 기간 선호는 계좌 신호를 보정합니다. 단기: starter/daily 강화, 장기: savings 강화',
    accountCondition:
        '우대조건 감수 수준은 조건 난이도 반영입니다. simple: starter 강화, active: savings/salary 강화',
    salaryTransfer:
        '급여이체 가능 선택 시 급여이체 신호가 있는 계좌에 +30점 보너스가 반영됩니다.',
    travelLevel:
        '해외 이용 빈도가 있을 때 travel/global 신호가 있는 계좌/카드에 추가 가점이 반영됩니다.',
    accountFilter:
        '계좌 필터 일치 신호는 항목별 +6점(기본 프로필)으로 누적 반영됩니다.',
} as const;

const cardWeightHints = {
    cardPriority:
        '카드 우선순위 가중치(기본 프로필): 생활할인 +24점, 연회비 절감 +26점, 여행/해외 +22점, 초보/저비용 +24점, 고정비 절감 +14점',
    annualFeeBudget:
        '연회비 허용 범위가 엄격하면 연회비 절감 우선순위로 고정됩니다. 저연회비 +8점, 고연회비 상품은 -6점 패널티(기본).',
    performance:
        '월 사용액/전월실적 달성 가능성이 높으면 +10점(기본 프로필) 가점이 반영됩니다.',
    travelLevel:
        '해외 결제 사용 빈도가 높으면 여행/해외 신호 카드에 +28점(기본 프로필) 가점이 반영됩니다.',
    cardFilter:
        '카드 필터 카테고리 일치 수만큼 항목당 +9점(기본 프로필)이 누적 반영됩니다.',
} as const;

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
type AccountTermPreference = (typeof accountTermOptions)[number]['key'];
type AccountConditionPreference = (typeof accountConditionOptions)[number]['key'];
type CardAnnualFeeBudget = (typeof cardAnnualFeeBudgetOptions)[number]['key'];
type CardPerformancePreference = (typeof cardPerformanceOptions)[number]['key'];

type FieldHintProps = {
    message: string;
};

const FieldHint = ({ message }: FieldHintProps) => (
    <span className="field-hint" tabIndex={0} role="button" aria-label="점수 반영 안내">
        ?
        <span className="field-hint-popup">{message}</span>
    </span>
);

function App() {
    const [profile, setProfile] = useState<SimulateRecommendationRequest>(initialProfile);
    const [activeTab, setActiveTab] = useState<ProductTab>('account');
    const [accountFilters, setAccountFilters] = useState<string[]>(['savings', 'salary']);
    const [cardFilters, setCardFilters] = useState<string[]>(['online', 'grocery', 'subscription']);
    const [accountTermPreference, setAccountTermPreference] = useState<AccountTermPreference>('middle');
    const [accountConditionPreference, setAccountConditionPreference] = useState<AccountConditionPreference>('balanced');
    const [cardAnnualFeeBudget, setCardAnnualFeeBudget] = useState<CardAnnualFeeBudget>('under10k');
    const [cardPerformancePreference, setCardPerformancePreference] = useState<CardPerformancePreference>('balanced');
    const [result, setResult] = useState<RecommendationRunResponse | null>(null);
    const [analytics, setAnalytics] = useState<RecommendationAnalyticsResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [redirectingKey, setRedirectingKey] = useState<string | null>(null);

    const annualFeeFirst = cardAnnualFeeBudget === 'free' || cardAnnualFeeBudget === 'under10k';
    const effectiveCardPriority = profile.cardPriority;

    const writeRunIdToUrl = (runId: string) => {
        const url = new URL(window.location.href);
        url.searchParams.set('runId', runId);
        window.history.replaceState({}, '', url.toString());
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
        setError(null);

        try {
            const response = await getRecommendationRun(normalizedRunId);
            setResult(response);
            writeRunIdToUrl(response.runId);
            await loadAnalytics(response.runId);
        } catch (e) {
            setError(e instanceof Error ? e.message : '추천 결과 조회에 실패했습니다.');
        }
    };
    useEffect(() => {
        const initialRunId = new URLSearchParams(window.location.search).get('runId');
        if (initialRunId) {
            void loadRun(initialRunId);
        }
    }, []);

    const onSubmit: React.FormEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();
        setLoading(true);
        setError(null);

        try {
            const mergedCategories = dedupe([...effectiveAccountCategories, ...effectiveCardCategories]);
            const response = await simulateRecommendations({
                ...profile,
                accountPriority: effectiveAccountPriority,
                cardPriority: effectiveCardPriority,
                priority: toLegacyPriority(effectiveAccountPriority, effectiveCardPriority),
                categories: mergedCategories,
                accountCategories: effectiveAccountCategories,
                cardCategories: effectiveCardCategories,
            });
            setResult(response);
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

    const effectiveAccountCategories = useMemo(() => {
        const categories = [...accountFilters];
        if (accountTermPreference === 'long') {
            categories.push('savings');
        } else if (accountTermPreference === 'short') {
            categories.push('starter', 'daily');
        }

        if (accountConditionPreference === 'simple') {
            categories.push('starter');
        } else if (accountConditionPreference === 'active') {
            categories.push('savings', 'salary');
        }

        if (profile.travelLevel !== 'none') {
            categories.push('travel');
        }

        return dedupe(categories);
    }, [accountFilters, accountTermPreference, accountConditionPreference, profile.travelLevel]);

    const effectiveAccountPriority = useMemo<AccountPriority>(() => {
        for (const key of accountFilters) {
            if (accountPriorityOrder.includes(key as AccountPriority)) {
                return key as AccountPriority;
            }
        }

        for (const key of accountPriorityOrder) {
            if (effectiveAccountCategories.includes(key)) {
                return key;
            }
        }

        return 'savings';
    }, [accountFilters, effectiveAccountCategories]);

    const effectiveCardCategories = useMemo(() => {
        const categories = [...cardFilters];

        if (cardAnnualFeeBudget === 'free' || cardAnnualFeeBudget === 'under10k') {
            categories.push('starter');
        } else if (cardAnnualFeeBudget === 'under20k') {
            categories.push('daily');
        }

        if (cardPerformancePreference === 'light') {
            categories.push('starter');
        } else if (cardPerformancePreference === 'high') {
            categories.push('savings');
        }

        if (profile.travelLevel !== 'none') {
            categories.push('travel');
        }

        return dedupe(categories);
    }, [cardFilters, cardAnnualFeeBudget, cardPerformancePreference, profile.travelLevel]);

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

    const resolveActionLabel = (item: RecommendationItem) => {
        if (item.productType === 'ACCOUNT') {
            return '공식 계좌 페이지 이동';
        }

        return '공식 카드 페이지 이동';
    };

    const highlightKeywords = useMemo(() => {
        const selectedAccountLabels = accountCategoryOptions
            .filter((category) => effectiveAccountCategories.includes(category.key))
            .map((category) => category.label);

        const selectedCardLabels = cardCategoryOptions
            .filter((category) => effectiveCardCategories.includes(category.key))
            .map((category) => category.label);

        const termLabel = accountTermOptions.find((option) => option.key === accountTermPreference)?.label ?? '';
        const conditionLabel =
            accountConditionOptions.find((option) => option.key === accountConditionPreference)?.label ?? '';
        const annualFeeLabel =
            cardAnnualFeeBudgetOptions.find((option) => option.key === cardAnnualFeeBudget)?.label ?? '';
        const performanceLabel =
            cardPerformanceOptions.find((option) => option.key === cardPerformancePreference)?.label ?? '';

        const rawKeywords = [
            ...selectedAccountLabels,
            ...selectedCardLabels,
            accountPriorityLabel[effectiveAccountPriority],
            cardPriorityLabel[effectiveCardPriority],
            termLabel,
            conditionLabel,
            annualFeeLabel,
            performanceLabel,
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
        effectiveAccountCategories,
        effectiveCardCategories,
        effectiveAccountPriority,
        effectiveCardPriority,
        annualFeeFirst,
        profile.salaryTransfer,
        profile.travelLevel,
        accountTermPreference,
        accountConditionPreference,
        cardAnnualFeeBudget,
        cardPerformancePreference,
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
                            <div className="input-pattern-note">
                                데이터 기반 입력 패턴: <strong>연회비 한도</strong>, <strong>전월실적 부담</strong>,
                                <strong> 혜택 카테고리</strong>를 먼저 입력하고,
                                계좌는 <strong>우대조건 달성 가능성</strong>과 <strong>기간 목적</strong>을 함께 반영합니다.
                            </div>

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
                                        <span className="label-title">
                                            운용 기간 선호
                                            <FieldHint message={accountWeightHints.accountTerm} />
                                        </span>
                                        <select
                                            value={accountTermPreference}
                                            onChange={(e) =>
                                                setAccountTermPreference(e.target.value as AccountTermPreference)
                                            }
                                        >
                                            {accountTermOptions.map((option) => (
                                                <option key={option.key} value={option.key}>
                                                    {option.label}
                                                </option>
                                            ))}
                                        </select>
                                    </label>

                                    <label>
                                        <span className="label-title">
                                            우대조건 감수 수준
                                            <FieldHint message={accountWeightHints.accountCondition} />
                                        </span>
                                        <select
                                            value={accountConditionPreference}
                                            onChange={(e) =>
                                                setAccountConditionPreference(e.target.value as AccountConditionPreference)
                                            }
                                        >
                                            {accountConditionOptions.map((option) => (
                                                <option key={option.key} value={option.key}>
                                                    {option.label}
                                                </option>
                                            ))}
                                        </select>
                                    </label>

                                    <label>
                                        <span className="label-title">
                                            급여이체 가능 여부
                                            <FieldHint message={accountWeightHints.salaryTransfer} />
                                        </span>
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
                                        <span className="label-title">
                                            해외 이용 빈도
                                            <FieldHint message={accountWeightHints.travelLevel} />
                                        </span>
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

                                    <fieldset className="category-group category-group-account">
                                        <legend>
                                            계좌 필터 (상품 성격)
                                            <FieldHint message={accountWeightHints.accountFilter} />
                                        </legend>
                                        <p className="category-help">
                                            계좌 필터를 카드형으로 선택하면 해당 성격이 점수에 직접 반영됩니다.
                                        </p>
                                        <div className="card-filter-grid">
                                            {accountCategoryOptions.map((category) => (
                                                <label key={category.key} className="card-filter-option">
                                                    <input
                                                        type="checkbox"
                                                        checked={accountFilters.includes(category.key)}
                                                        onChange={() => toggleAccountFilter(category.key)}
                                                    />
                                                    <span className="card-filter-option-content">
                                                        <img
                                                            src={category.icon}
                                                            alt=""
                                                            aria-hidden="true"
                                                            className="card-filter-icon"
                                                        />
                                                        <span className="card-filter-label">{category.label}</span>
                                                    </span>
                                                </label>
                                            ))}
                                        </div>
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
                                        <span className="label-title">
                                            카드 추천 기준
                                            <FieldHint message={cardWeightHints.cardPriority} />
                                        </span>
                                        <select
                                            value={profile.cardPriority}
                                            onChange={(e) => {
                                                const cardPriority = e.target.value as CardPriority;
                                                setProfile((prev) => ({
                                                    ...prev,
                                                    cardPriority,
                                                    priority: toLegacyPriority(effectiveAccountPriority, cardPriority),
                                                }));
                                            }}
                                        >
                                            {Object.entries(cardPriorityLabel).map(([key, value]) => (
                                                <option key={key} value={key}>
                                                    {value}
                                                </option>
                                            ))}
                                        </select>
                                    </label>

                                    <label>
                                        <span className="label-title">
                                            연회비 허용 범위
                                            <FieldHint message={cardWeightHints.annualFeeBudget} />
                                        </span>
                                        <select
                                            value={cardAnnualFeeBudget}
                                            onChange={(e) =>
                                                setCardAnnualFeeBudget(e.target.value as CardAnnualFeeBudget)
                                            }
                                        >
                                            {cardAnnualFeeBudgetOptions.map((option) => (
                                                <option key={option.key} value={option.key}>
                                                    {option.label}
                                                </option>
                                            ))}
                                        </select>
                                    </label>

                                    <label>
                                        <span className="label-title">
                                            전월실적 달성 자신도
                                            <FieldHint message={cardWeightHints.performance} />
                                        </span>
                                        <select
                                            value={cardPerformancePreference}
                                            onChange={(e) =>
                                                setCardPerformancePreference(e.target.value as CardPerformancePreference)
                                            }
                                        >
                                            {cardPerformanceOptions.map((option) => (
                                                <option key={option.key} value={option.key}>
                                                    {option.label}
                                                </option>
                                            ))}
                                        </select>
                                    </label>

                                    <label>
                                        <span className="label-title">
                                            해외 결제 사용 빈도
                                            <FieldHint message={cardWeightHints.travelLevel} />
                                        </span>
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
                                        <legend>
                                            카드 필터 (혜택 카테고리)
                                            <FieldHint message={cardWeightHints.cardFilter} />
                                        </legend>
                                        <p className="category-help">
                                            혜택 카테고리 + 연회비 + 실적 난이도를 결합해 개인화 점수를 계산합니다.
                                        </p>
                                        <div className="card-filter-grid">
                                            {cardCategoryOptions.map((category) => (
                                                <label key={category.key} className="card-filter-option">
                                                    <input
                                                        type="checkbox"
                                                        checked={cardFilters.includes(category.key)}
                                                        onChange={() => toggleCardFilter(category.key)}
                                                    />
                                                    <span className="card-filter-option-content">
                                                        <img
                                                            src={category.icon}
                                                            alt=""
                                                            aria-hidden="true"
                                                            className="card-filter-icon"
                                                        />
                                                        <span className="card-filter-label">{category.label}</span>
                                                    </span>
                                                </label>
                                            ))}
                                        </div>
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
