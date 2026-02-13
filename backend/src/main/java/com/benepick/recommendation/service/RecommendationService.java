package com.benepick.recommendation.service;

import com.benepick.recommendation.dto.RecommendationBundleBenefitComponentResponse;
import com.benepick.recommendation.dto.RecommendationBundleResponse;
import com.benepick.recommendation.dto.RecommendationDetailFieldResponse;
import com.benepick.recommendation.dto.RecommendationItemResponse;
import com.benepick.recommendation.dto.RecommendationRedirectRequest;
import com.benepick.recommendation.dto.RecommendationRedirectResponse;
import com.benepick.recommendation.dto.RecommendationRunHistoryItemResponse;
import com.benepick.recommendation.dto.RecommendationRunResponse;
import com.benepick.recommendation.dto.SimulateRecommendationRequest;
import com.benepick.recommendation.entity.AccountCatalogEntity;
import com.benepick.recommendation.entity.CardCatalogEntity;
import com.benepick.recommendation.entity.RecommendationItemEntity;
import com.benepick.recommendation.entity.RecommendationRedirectEventEntity;
import com.benepick.recommendation.entity.RecommendationRunEntity;
import com.benepick.recommendation.repository.AccountCatalogRepository;
import com.benepick.recommendation.repository.CardCatalogRepository;
import com.benepick.recommendation.repository.RecommendationItemRepository;
import com.benepick.recommendation.repository.RecommendationRedirectEventRepository;
import com.benepick.recommendation.repository.RecommendationRunRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationService {

    private static final Pattern MAX_RATE_PATTERN = Pattern.compile("최고\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern BASE_RATE_PATTERN = Pattern.compile("기본\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern ANNUAL_FEE_MAN_WON_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*만원");
    private static final Pattern ANNUAL_FEE_WON_PATTERN = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,7})\\s*원?");
    private static final Pattern CARD_PERCENT_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern CARD_AMOUNT_PATTERN = Pattern.compile(
        "(월\\s*최대\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?\\s*(?:만원|원)|"
            + "최대\\s*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?\\s*(?:만원|원)|"
            + "[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?\\s*(?:만원|원))"
    );
    private static final Set<String> CARD_BENEFIT_KEYWORDS = Set.of(
        "할인",
        "캐시백",
        "적립",
        "청구",
        "환급",
        "포인트",
        "마일",
        "리워드",
        "혜택",
        "우대",
        "한도",
        "최대",
        "월"
    );

    private static final Map<String, String> CATEGORY_ALIASES = buildCategoryAliases();
    private static final Map<String, String> CATEGORY_LABELS = buildCategoryLabels();
    private static final List<CategoryKeywordRule> CATEGORY_KEYWORD_RULES = buildCategoryKeywordRules();

    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final RecommendationRedirectEventRepository recommendationRedirectEventRepository;
    private final AccountCatalogRepository accountCatalogRepository;
    private final CardCatalogRepository cardCatalogRepository;
    private final RecommendationScoringProperties scoringProperties;
    private final ProductUrlOverrideService productUrlOverrideService;

    public RecommendationService(
        RecommendationRunRepository recommendationRunRepository,
        RecommendationItemRepository recommendationItemRepository,
        RecommendationRedirectEventRepository recommendationRedirectEventRepository,
        AccountCatalogRepository accountCatalogRepository,
        CardCatalogRepository cardCatalogRepository,
        RecommendationScoringProperties scoringProperties,
        ProductUrlOverrideService productUrlOverrideService
    ) {
        this.recommendationRunRepository = recommendationRunRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.recommendationRedirectEventRepository = recommendationRedirectEventRepository;
        this.accountCatalogRepository = accountCatalogRepository;
        this.cardCatalogRepository = cardCatalogRepository;
        this.scoringProperties = scoringProperties;
        this.productUrlOverrideService = productUrlOverrideService;
    }

    @Transactional
    public RecommendationRunResponse simulate(SimulateRecommendationRequest request) {
        Map<String, String> officialUrlOverrides = productUrlOverrideService.loadOverrides();
        List<RankedProduct> rankedAccounts = rankAccounts(request, officialUrlOverrides);
        List<RankedProduct> rankedCards = rankCards(request, officialUrlOverrides);

        int expectedNetMonthlyProfit = estimateNetMonthlyProfit(rankedAccounts, rankedCards);

        RecommendationRunEntity run = recommendationRunRepository.save(
            new RecommendationRunEntity(request.priority().toUpperCase(), expectedNetMonthlyProfit)
        );

        List<RecommendationItemEntity> savedItems = new ArrayList<>();
        for (RankedProduct ranked : rankedAccounts) {
            savedItems.add(toEntity(run, ranked));
        }
        for (RankedProduct ranked : rankedCards) {
            savedItems.add(toEntity(run, ranked));
        }
        recommendationItemRepository.saveAll(savedItems);

        List<RecommendationItemResponse> accounts = toItemResponses(rankedAccounts);
        List<RecommendationItemResponse> cards = toItemResponses(rankedCards);

        return new RecommendationRunResponse(
            run.getId(),
            normalize(run.getPriority()),
            expectedNetMonthlyProfit,
            accounts,
            cards,
            List.of()
        );
    }

    @Transactional(readOnly = true)
    public RecommendationRunResponse getRun(UUID runId) {
        RecommendationRunEntity run = recommendationRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation run not found"));

        List<RecommendationItemEntity> items = recommendationItemRepository
            .findByRecommendationRun_IdOrderByProductTypeAscRankAsc(runId);

        Map<String, String> officialUrlOverrides = productUrlOverrideService.loadOverrides();

        List<RecommendationItemResponse> accounts = items.stream()
            .filter(item -> "ACCOUNT".equals(item.getProductType()))
            .sorted(Comparator.comparingInt(RecommendationItemEntity::getRank))
            .map(item -> toItemResponse(item, officialUrlOverrides))
            .toList();

        List<RecommendationItemResponse> cards = items.stream()
            .filter(item -> "CARD".equals(item.getProductType()))
            .sorted(Comparator.comparingInt(RecommendationItemEntity::getRank))
            .map(item -> toItemResponse(item, officialUrlOverrides))
            .toList();

        return new RecommendationRunResponse(
            run.getId(),
            normalize(run.getPriority()),
            run.getExpectedNetMonthlyProfit(),
            accounts,
            cards,
            List.of()
        );
    }

    @Transactional(readOnly = true)
    public List<RecommendationRunHistoryItemResponse> getRecentRuns(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 30));

        List<RecommendationRunEntity> runs = recommendationRunRepository
            .findAllByOrderByCreatedAtDesc(PageRequest.of(0, normalizedLimit));

        return runs.stream()
            .map(run -> new RecommendationRunHistoryItemResponse(
                run.getId(),
                normalize(run.getPriority()),
                run.getExpectedNetMonthlyProfit(),
                recommendationRedirectEventRepository.countByRecommendationRunId(run.getId()),
                run.getCreatedAt()
            ))
            .toList();
    }

    @Transactional
    public RecommendationRedirectResponse redirect(
        UUID runId,
        RecommendationRedirectRequest request,
        String userAgent,
        String ipAddress,
        String referrer
    ) {
        recommendationRunRepository.findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation run not found"));

        String normalizedType = request.productType().toUpperCase();

        RecommendationItemEntity item = recommendationItemRepository
            .findByRecommendationRun_IdAndProductTypeAndProductId(runId, normalizedType, request.productId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation item not found"));

        Map<String, String> officialUrlOverrides = productUrlOverrideService.loadOverrides();
        String resolvedOfficialUrl = resolveRedirectOfficialUrl(item, normalizedType, officialUrlOverrides);

        RecommendationRedirectEventEntity event = new RecommendationRedirectEventEntity(
            runId,
            normalizedType,
            request.productId(),
            resolvedOfficialUrl,
            userAgent,
            ipAddress,
            referrer
        );
        recommendationRedirectEventRepository.save(event);

        return new RecommendationRedirectResponse(resolvedOfficialUrl);
    }

    private String resolveRedirectOfficialUrl(
        RecommendationItemEntity item,
        String productType,
        Map<String, String> officialUrlOverrides
    ) {
        String catalogUrl = resolveCatalogOfficialUrl(item, productType);
        String resolvedOfficialUrl = resolveOfficialUrlForProduct(
            item.getProductId(),
            productType,
            item.getProviderName(),
            item.getProductName(),
            catalogUrl,
            officialUrlOverrides
        );
        return resolveOfficialLinkPlan(
            productType,
            item.getProviderName(),
            item.getProductName(),
            resolvedOfficialUrl
        ).redirectUrl();
    }

    private String resolveCatalogOfficialUrl(RecommendationItemEntity item, String productType) {
        if ("ACCOUNT".equals(productType)) {
            return accountCatalogRepository.findByProductKey(item.getProductId())
                .map(AccountCatalogEntity::getOfficialUrl)
                .filter(url -> !normalize(url).isBlank())
                .orElse(item.getOfficialUrl());
        }

        if ("CARD".equals(productType)) {
            return cardCatalogRepository.findByProductKey(item.getProductId())
                .map(CardCatalogEntity::getOfficialUrl)
                .filter(url -> !normalize(url).isBlank())
                .orElse(item.getOfficialUrl());
        }

        return item.getOfficialUrl();
    }

    private List<RankedProduct> rankAccounts(
        SimulateRecommendationRequest request,
        Map<String, String> officialUrlOverrides
    ) {
        String priority = resolveAccountPriority(request);
        String salaryTransfer = normalize(request.salaryTransfer());
        String travelLevel = normalize(request.travelLevel());
        Set<String> userCategories = resolveAccountUserCategories(request);
        RecommendationScoringProperties.Account accountScore = scoringProperties.resolvedAccount();
        Set<String> accountIntentSignals = buildAccountIntentSignals(request, priority, salaryTransfer, travelLevel, userCategories, accountScore);

        List<AccountCatalogEntity> candidates = accountCatalogRepository.findByActiveTrue();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account catalog is empty");
        }

        List<ScoredProduct> scored = candidates.stream()
            .map(candidate -> {
                int score = accountScore.getBaseScore();
                List<String> reasons = new ArrayList<>();
                List<ScoreReasonPart> scoreParts = new ArrayList<>();
                addScorePart(scoreParts, "기본점수", accountScore.getBaseScore());

                Set<String> accountSignals = deriveAccountSignals(candidate);
                Set<String> matchedIntentSignals = intersection(accountSignals, accountIntentSignals);

                RateInfo rateInfo = extractRateInfo(candidate.getSummary());
                if (rateInfo.maxRate() != null) {
                    reasons.add("최고 금리 " + formatPercent(rateInfo.maxRate()) + "% (상품 요약 기준)");
                    if (rateInfo.maxRate() >= accountScore.getHighRateThreshold()) {
                        int bonus = accountScore.getHighRateBonusWeight();
                        score += bonus;
                        addScorePart(scoreParts, "고금리 보너스", bonus);
                    }
                }
                if (rateInfo.baseRate() != null) {
                    reasons.add("기본 금리 " + formatPercent(rateInfo.baseRate()) + "% 확인");
                }

                if ("yes".equals(salaryTransfer) && accountSignals.contains("salary")) {
                    int bonus = accountScore.getSalaryTransferWeight();
                    score += bonus;
                    addScorePart(scoreParts, "급여이체 우대", bonus);
                    reasons.add("급여이체 조건 충족 시 우대 혜택 가능");
                }

                switch (priority) {
                    case "savings" -> {
                        if (accountSignals.contains("savings")) {
                            int bonus = accountScore.getPrioritySavingsWeight();
                            score += bonus;
                            addScorePart(scoreParts, "우선순위(저축/금리)", bonus);
                            reasons.add("저축/금리 우선순위와 상품 성격 일치");
                        }
                    }
                    case "salary" -> {
                        if (accountSignals.contains("salary")) {
                            int bonus = accountScore.getPrioritySalaryWeight();
                            score += bonus;
                            addScorePart(scoreParts, "우선순위(급여이체/주거래)", bonus);
                            reasons.add("급여이체/주거래 중심 우선순위와 일치");
                        }
                    }
                    case "starter" -> {
                        if (accountSignals.contains("starter")) {
                            int bonus = accountScore.getPriorityStarterWeight();
                            score += bonus;
                            addScorePart(scoreParts, "우선순위(초보/저비용)", bonus);
                            reasons.add("초기 이용자 친화 조건과 일치");
                        }
                    }
                    case "travel" -> {
                        if (accountSignals.contains("travel") || accountSignals.contains("global")) {
                            int bonus = accountScore.getPriorityTravelWeight();
                            score += bonus;
                            addScorePart(scoreParts, "우선순위(여행/해외)", bonus);
                            reasons.add("여행/외화 중심 우선순위 반영");
                        }
                    }
                    case "cashback" -> {
                        if (accountSignals.contains("daily") || accountSignals.contains("salary")) {
                            int bonus = accountScore.getPriorityCashbackWeight();
                            score += bonus;
                            addScorePart(scoreParts, "우선순위(생활할인)", bonus);
                            reasons.add("생활소비 연동형 계좌 조건과 맞음");
                        }
                    }
                    default -> {
                    }
                }

                if ("often".equals(travelLevel)
                    && (accountSignals.contains("global") || accountSignals.contains("travel"))) {
                    int bonus = accountScore.getTravelOftenGlobalWeight();
                    score += bonus;
                    addScorePart(scoreParts, "해외 이용 빈도", bonus);
                    reasons.add("해외 이용 빈도에 적합한 신호 확인");
                }

                if (request.age() <= accountScore.getYoungAgeMax() && accountSignals.contains("starter")) {
                    int bonus = accountScore.getYoungWeight();
                    score += bonus;
                    addScorePart(scoreParts, "연령 우대", bonus);
                    reasons.add("연령 구간에 맞는 우대/간편형 조건");
                }

                if (request.monthlySpend() >= accountScore.getDailySpendThreshold() && accountSignals.contains("daily")) {
                    int bonus = accountScore.getDailySpendWeight();
                    score += bonus;
                    addScorePart(scoreParts, "생활비 흐름 매칭", bonus);
                    reasons.add("생활비 흐름과 연결되는 계좌 패턴");
                }

                if (!matchedIntentSignals.isEmpty()) {
                    int bonus = matchedIntentSignals.size() * accountScore.getIntentCategoryHitWeight();
                    score += bonus;
                    addScorePart(scoreParts, "의도 신호 일치 x" + matchedIntentSignals.size(), bonus);
                    reasons.add("일치 신호: " + labelsOf(matchedIntentSignals));
                }

                int finalScore = Math.max(0, score);
                String reasonText = buildReasonWithScore(
                    scoreParts,
                    reasons,
                    finalScore,
                    "총점 동점 시 기관명/상품명 순"
                );
                ProductBenefitEstimate benefitEstimate = estimateProductBenefit(
                    "ACCOUNT",
                    finalScore,
                    scoreParts,
                    reasonText
                );

                String resolvedOfficialUrl = resolveOfficialUrlForProduct(
                    candidate.getProductKey(),
                    "ACCOUNT",
                    candidate.getProviderName(),
                    candidate.getProductName(),
                    candidate.getOfficialUrl(),
                    officialUrlOverrides
                );

                return new ScoredProduct(
                    "ACCOUNT",
                    candidate.getProductKey(),
                    candidate.getProviderName(),
                    candidate.getProductName(),
                    candidate.getSummary(),
                    candidate.getAccountKind() + " 계좌",
                    finalScore,
                    reasonText,
                    benefitEstimate.minExpectedMonthlyBenefit(),
                    benefitEstimate.expectedMonthlyBenefit(),
                    benefitEstimate.maxExpectedMonthlyBenefit(),
                    benefitEstimate.estimateMethod(),
                    benefitEstimate.benefitComponents(),
                    resolvedOfficialUrl,
                    buildAccountDetailFields(candidate, resolvedOfficialUrl)
                );
            })
            .sorted(Comparator.comparingInt(ScoredProduct::score).reversed()
                .thenComparing(ScoredProduct::provider)
                .thenComparing(ScoredProduct::name))
            .limit(3)
            .toList();

        return assignRank(scored);
    }

    private List<RankedProduct> rankCards(
        SimulateRecommendationRequest request,
        Map<String, String> officialUrlOverrides
    ) {
        String priority = resolveCardPriority(request);
        String travelLevel = normalize(request.travelLevel());
        Set<String> userCategories = resolveCardUserCategories(request);

        List<CardCatalogEntity> candidates = cardCatalogRepository.findByActiveTrue().stream()
            .filter(candidate -> !lowerSet(candidate.getTags()).contains("stat-only"))
            .toList();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Card catalog is empty");
        }

        RecommendationScoringProperties.Card cardScore = scoringProperties.resolvedCard();

        List<ScoredProduct> scored = candidates.stream()
            .map(candidate -> {
                int score = cardScore.getBaseScore();
                List<String> reasons = new ArrayList<>();
                List<ScoreReasonPart> scoreParts = new ArrayList<>();
                addScorePart(scoreParts, "기본점수", cardScore.getBaseScore());

                Set<String> tagSignals = canonicalizeCategories(candidate.getTags());
                Set<String> cardCategories = deriveCardCategories(candidate);
                Set<String> matchedCategories = intersection(cardCategories, userCategories);

                int categoryHit = matchedCategories.size();
                if (categoryHit > 0) {
                    int bonus = categoryHit * cardScore.getCategoryHitWeight();
                    score += bonus;
                    addScorePart(scoreParts, "카테고리 일치 x" + categoryHit, bonus);
                    reasons.add("소비 카테고리 일치: " + labelsOf(matchedCategories));
                }

                switch (priority) {
                    case "cashback" -> {
                        if (tagSignals.contains("daily") || tagSignals.contains("online")) {
                            int bonus = cardScore.getPriorityCashbackWeight();
                            score += bonus;
                            addScorePart(scoreParts, "우선순위(생활할인)", bonus);
                            reasons.add("생활 할인/캐시백 우선순위와 일치");
                        }
                    }
                    case "travel" -> {
                        if (cardCategories.contains("travel") || tagSignals.contains("travel")) {
                            int bonus = cardScore.getPriorityTravelWeight();
                            score += bonus;
                            addScorePart(scoreParts, "우선순위(여행/해외)", bonus);
                            reasons.add("여행/해외결제 우선순위 반영");
                        }
                    }
                    case "starter" -> {
                        if (cardCategories.contains("starter") || tagSignals.contains("starter")) {
                            int bonus = cardScore.getPriorityStarterWeight();
                            score += bonus;
                            addScorePart(scoreParts, "우선순위(초보/저비용)", bonus);
                            reasons.add("연회비 부담 최소 선호와 일치");
                        }
                    }
                    case "savings" -> {
                        if (cardCategories.contains("daily") || tagSignals.contains("online")) {
                            int bonus = cardScore.getPrioritySavingsWeight();
                            score += bonus;
                            addScorePart(scoreParts, "우선순위(저축/절감)", bonus);
                            reasons.add("저축 우선순위에 맞는 고정비/생활비 절감형");
                        }
                    }
                    default -> {
                    }
                }

                if ("often".equals(travelLevel)
                    && (cardCategories.contains("travel") || tagSignals.contains("travel"))) {
                    int bonus = cardScore.getTravelOftenWeight();
                    score += bonus;
                    addScorePart(scoreParts, "해외 이용 빈도", bonus);
                    reasons.add("해외 이용 빈도에 유리한 혜택 구성");
                }

                if (request.monthlySpend() >= cardScore.getDailySpendThreshold()
                    && (tagSignals.contains("daily") || hasLifestyleCategory(cardCategories))) {
                    int bonus = cardScore.getDailySpendWeight();
                    score += bonus;
                    addScorePart(scoreParts, "전월실적 달성 가능성", bonus);
                    reasons.add("전월 실적 달성 가능성이 높은 소비 패턴");
                }

                String normalizedAnnualFeeText = normalizeAnnualFeeText(candidate.getAnnualFeeText());
                AnnualFeeInfo annualFeeInfo = parseAnnualFee(normalizedAnnualFeeText);
                if (annualFeeInfo.lowFee()) {
                    int bonus = cardScore.getLowAnnualFeeBonusWeight();
                    score += bonus;
                    addScorePart(scoreParts, "연회비 저부담", bonus);
                    reasons.add("연회비 부담이 낮음 (" + normalizedAnnualFeeText + ")");
                } else if (annualFeeInfo.estimatedWon() != null
                    && annualFeeInfo.estimatedWon() >= cardScore.getHighAnnualFeeThresholdWon()) {
                    int penalty = cardScore.getHighAnnualFeePenaltyWeight();
                    score -= penalty;
                    addScorePart(scoreParts, "연회비 패널티", -penalty);
                    reasons.add("연회비 수준 고려 필요 (" + normalizedAnnualFeeText + ")");
                } else {
                    reasons.add("연회비 정보 반영 (" + normalizedAnnualFeeText + ")");
                }

                if ("annualfee".equals(priority)) {
                    if (annualFeeInfo.lowFee()) {
                        int bonus = cardScore.getPriorityAnnualFeeWeight();
                        score += bonus;
                        addScorePart(scoreParts, "우선순위(연회비 절감)", bonus);
                        reasons.add("연회비 절감 우선순위와 일치");
                    } else if (annualFeeInfo.estimatedWon() != null
                        && annualFeeInfo.estimatedWon() >= cardScore.getHighAnnualFeeThresholdWon()) {
                        int penalty = Math.max(1, cardScore.getPriorityAnnualFeeWeight() / 2);
                        score -= penalty;
                        addScorePart(scoreParts, "우선순위(연회비 절감) 패널티", -penalty);
                        reasons.add("연회비 절감 우선순위 대비 비용 부담이 큼");
                    }
                }

                String summaryHighlight = summaryHighlight(candidate.getSummary());
                String quantifiedBenefit = summarizeCardQuantifiedBenefits(candidate);
                if (!quantifiedBenefit.startsWith("정량 혜택 정보 없음")) {
                    reasons.add("혜택 수치: " + quantifiedBenefit);
                } else if (!summaryHighlight.isBlank()) {
                    reasons.add("핵심 혜택: " + summaryHighlight);
                }

                int finalScore = Math.max(0, score);
                String reasonText = buildReasonWithScore(
                    scoreParts,
                    reasons,
                    finalScore,
                    "총점 동점 시 기관명/상품명 순"
                );
                ProductBenefitEstimate benefitEstimate = estimateProductBenefit(
                    "CARD",
                    finalScore,
                    scoreParts,
                    reasonText
                );

                String resolvedOfficialUrl = resolveOfficialUrlForProduct(
                    candidate.getProductKey(),
                    "CARD",
                    candidate.getProviderName(),
                    candidate.getProductName(),
                    candidate.getOfficialUrl(),
                    officialUrlOverrides
                );

                return new ScoredProduct(
                    "CARD",
                    candidate.getProductKey(),
                    candidate.getProviderName(),
                    candidate.getProductName(),
                    candidate.getSummary(),
                    normalizedAnnualFeeText,
                    finalScore,
                    reasonText,
                    benefitEstimate.minExpectedMonthlyBenefit(),
                    benefitEstimate.expectedMonthlyBenefit(),
                    benefitEstimate.maxExpectedMonthlyBenefit(),
                    benefitEstimate.estimateMethod(),
                    benefitEstimate.benefitComponents(),
                    resolvedOfficialUrl,
                    buildCardDetailFields(candidate, resolvedOfficialUrl)
                );
            })
            .sorted(Comparator.comparingInt(ScoredProduct::score).reversed()
                .thenComparing(ScoredProduct::provider)
                .thenComparing(ScoredProduct::name))
            .limit(3)
            .toList();

        return assignRank(scored);
    }

    private List<RecommendationBundleResponse> buildBundles(
        List<RecommendationItemResponse> accounts,
        List<RecommendationItemResponse> cards
    ) {
        if (accounts.isEmpty() || cards.isEmpty()) {
            return List.of();
        }

        List<BundleCandidate> bundles = new ArrayList<>();
        Set<String> usedPairs = new HashSet<>();

        addBundleIfNew(bundles, usedPairs, "주거래 집중 패키지", accounts.get(0), cards.get(0));

        if (accounts.size() > 1) {
            addBundleIfNew(bundles, usedPairs, "저축 + 생활 최적화 패키지", accounts.get(1), cards.get(0));
        }
        if (cards.size() > 1) {
            addBundleIfNew(bundles, usedPairs, "실적 보완 서브카드 패키지", accounts.get(0), cards.get(1));
        }

        for (int i = 0; i < accounts.size() && bundles.size() < 3; i++) {
            for (int j = 0; j < cards.size() && bundles.size() < 3; j++) {
                addBundleIfNew(bundles, usedPairs, "균형형 패키지", accounts.get(i), cards.get(j));
            }
        }

        return bundles.stream()
            .limit(3)
            .map(bundle -> new RecommendationBundleResponse(
                bundle.rank(),
                bundle.title(),
                bundle.accountProductId(),
                bundle.accountLabel(),
                bundle.cardProductId(),
                bundle.cardLabel(),
                bundle.minExtraMonthlyBenefit(),
                bundle.expectedExtraMonthlyBenefit(),
                bundle.maxExtraMonthlyBenefit(),
                bundle.accountExpectedExtraMonthlyBenefit(),
                bundle.cardExpectedExtraMonthlyBenefit(),
                bundle.synergyExtraMonthlyBenefit(),
                bundle.estimateMethod(),
                bundle.benefitComponents(),
                bundle.reason()
            ))
            .toList();
    }

    private void addBundleIfNew(
        List<BundleCandidate> bundles,
        Set<String> usedPairs,
        String title,
        RecommendationItemResponse account,
        RecommendationItemResponse card
    ) {
        String pairKey = account.productId() + "::" + card.productId();
        if (usedPairs.contains(pairKey)) {
            return;
        }

        usedPairs.add(pairKey);

        BundleBenefitEstimate estimate = estimateBundleBenefit(account, card);

        bundles.add(new BundleCandidate(
            bundles.size() + 1,
            title,
            account.productId(),
            account.provider() + " · " + account.name(),
            card.productId(),
            card.provider() + " · " + card.name(),
            estimate.minExtraMonthlyBenefit(),
            estimate.expectedExtraMonthlyBenefit(),
            estimate.maxExtraMonthlyBenefit(),
            estimate.accountExpectedExtraMonthlyBenefit(),
            estimate.cardExpectedExtraMonthlyBenefit(),
            estimate.synergyExtraMonthlyBenefit(),
            estimate.estimateMethod(),
            estimate.benefitComponents(),
            buildBundleReason(account, card, estimate.totalSynergyBonus())
        ));
    }

    private BundleBenefitEstimate estimateBundleBenefit(
        RecommendationItemResponse account,
        RecommendationItemResponse card
    ) {
        int accountBaseFromScore = Math.max(0, account.score() * 42);
        int cardBaseFromScore = Math.max(0, card.score() * 42);

        List<RecommendationBundleBenefitComponentResponse> components =
            buildBundleBenefitComponents(account, card, accountBaseFromScore, cardBaseFromScore);

        int accountExpectedExtraMonthlyBenefit = sumAppliedBenefitByPrefix(components, "account_");
        int cardExpectedExtraMonthlyBenefit = sumAppliedBenefitByPrefix(components, "card_");
        int synergyExtraMonthlyBenefit = sumAppliedBenefitByPrefix(components, "synergy_");

        int expectedExtraMonthlyBenefit = Math.max(
            6000,
            accountExpectedExtraMonthlyBenefit + cardExpectedExtraMonthlyBenefit + synergyExtraMonthlyBenefit
        );

        int baseTotal = accountBaseFromScore + cardBaseFromScore;
        int variableBonus = components.stream()
            .filter(RecommendationBundleBenefitComponentResponse::applied)
            .filter(component -> !component.key().endsWith("_base_score"))
            .mapToInt(RecommendationBundleBenefitComponentResponse::amountWonPerMonth)
            .sum();

        int minExtraMonthlyBenefit = Math.max(
            6000,
            baseTotal + (int) Math.round(variableBonus * 0.4d)
        );
        int maxExtraMonthlyBenefit = Math.max(
            expectedExtraMonthlyBenefit,
            Math.max(6000, baseTotal + (int) Math.round(variableBonus * 1.2d))
        );

        String estimateMethod = "룰 기반 추정치(계좌 이득 + 카드 이득 + 조합 보너스)";

        return new BundleBenefitEstimate(
            minExtraMonthlyBenefit,
            expectedExtraMonthlyBenefit,
            maxExtraMonthlyBenefit,
            synergyExtraMonthlyBenefit,
            accountExpectedExtraMonthlyBenefit,
            cardExpectedExtraMonthlyBenefit,
            synergyExtraMonthlyBenefit,
            estimateMethod,
            components
        );
    }

    private List<RecommendationBundleBenefitComponentResponse> buildBundleBenefitComponents(
        RecommendationItemResponse account,
        RecommendationItemResponse card,
        int accountBaseFromScore,
        int cardBaseFromScore
    ) {
        String accountText = normalize(account.summary() + " " + account.reason() + " " + account.meta());
        String cardText = normalize(card.summary() + " " + card.reason() + " " + card.meta());

        List<RecommendationBundleBenefitComponentResponse> components = new ArrayList<>();
        components.add(new RecommendationBundleBenefitComponentResponse(
            "account_base_score",
            "계좌 기본 절감액",
            "계좌 추천 점수 환산",
            accountBaseFromScore,
            true
        ));
        components.add(new RecommendationBundleBenefitComponentResponse(
            "card_base_score",
            "카드 기본 절감액",
            "카드 추천 점수 환산",
            cardBaseFromScore,
            true
        ));

        addBundleConditionComponent(
            components,
            "account_salary_transfer",
            "계좌: 급여이체 우대 보너스",
            "급여이체 및 우대조건 유지",
            5200,
            accountText.contains("급여")
        );

        addBundleConditionComponent(
            components,
            "account_savings_rate",
            "계좌: 저축/금리 우대 보너스",
            "저축·금리 우대조건 유지",
            3600,
            accountText.contains("저축") || accountText.contains("금리")
        );

        addBundleConditionComponent(
            components,
            "card_monthly_performance",
            "카드: 전월실적 달성 보너스",
            "카드 전월실적 충족",
            4200,
            cardText.contains("전월") || cardText.contains("실적")
        );

        addBundleConditionComponent(
            components,
            "card_category_spend",
            "카드: 카테고리 소비 매칭 보너스",
            "주요 소비 카테고리 사용 유지",
            3200,
            cardText.contains("카테고리") || cardText.contains("생활")
        );

        addBundleConditionComponent(
            components,
            "synergy_global_travel",
            "조합: 여행/외화 연동 보너스",
            "해외/외화 사용 조건 충족",
            2800,
            (cardText.contains("여행") || cardText.contains("해외")) && accountText.contains("외화")
        );

        return components;
    }

    private void addBundleConditionComponent(
        List<RecommendationBundleBenefitComponentResponse> components,
        String key,
        String label,
        String condition,
        int amountWonPerMonth,
        boolean applied
    ) {
        components.add(new RecommendationBundleBenefitComponentResponse(
            key,
            label,
            condition,
            amountWonPerMonth,
            applied
        ));
    }

    private int sumAppliedBenefitByPrefix(
        List<RecommendationBundleBenefitComponentResponse> components,
        String keyPrefix
    ) {
        return components.stream()
            .filter(RecommendationBundleBenefitComponentResponse::applied)
            .filter(component -> component.key().startsWith(keyPrefix))
            .mapToInt(RecommendationBundleBenefitComponentResponse::amountWonPerMonth)
            .sum();
    }

    private String buildBundleReason(
        RecommendationItemResponse account,
        RecommendationItemResponse card,
        int synergyBonus
    ) {
        List<String> reasons = new ArrayList<>();
        reasons.add("계좌(" + account.rank() + "순위)와 카드(" + card.rank() + "순위) 조합 최적화");

        if (synergyBonus >= 9000) {
            reasons.add("우대조건/실적 동시 달성 가능성이 높음");
        } else if (synergyBonus >= 5000) {
            reasons.add("우대조건 달성에 유리한 조합");
        }

        reasons.add("추천 사유 결합: "
            + extractCoreReason(account.reason())
            + " + "
            + extractCoreReason(card.reason()));
        return joinReasons(reasons);
    }


    private Set<String> resolveAccountUserCategories(SimulateRecommendationRequest request) {
        Set<String> accountCategories = canonicalizeCategories(request.accountCategories());
        if (!accountCategories.isEmpty()) {
            return accountCategories;
        }
        return canonicalizeCategories(request.categories());
    }

    private Set<String> resolveCardUserCategories(SimulateRecommendationRequest request) {
        Set<String> cardCategories = canonicalizeCategories(request.cardCategories());
        if (!cardCategories.isEmpty()) {
            return cardCategories;
        }
        return canonicalizeCategories(request.categories());
    }

    private Set<String> buildAccountIntentSignals(
        SimulateRecommendationRequest request,
        String priority,
        String salaryTransfer,
        String travelLevel,
        Set<String> userCategories,
        RecommendationScoringProperties.Account accountScore
    ) {
        Set<String> signals = new HashSet<>();

        if ("yes".equals(salaryTransfer)) {
            signals.add("salary");
        }

        if ("travel".equals(priority)) {
            signals.add("travel");
            signals.add("global");
        } else if ("savings".equals(priority)) {
            signals.add("savings");
        } else if ("starter".equals(priority)) {
            signals.add("starter");
        } else if ("salary".equals(priority)) {
            signals.add("salary");
            signals.add("daily");
        } else if ("cashback".equals(priority)) {
            signals.add("daily");
        }

        if ("often".equals(travelLevel) || "sometimes".equals(travelLevel)) {
            signals.add("travel");
        }

        if (hasLifestyleCategory(userCategories) || request.monthlySpend() >= accountScore.getDailySpendThreshold()) {
            signals.add("daily");
        }

        if (request.age() <= accountScore.getYoungAgeMax()) {
            signals.add("starter");
        }

        return signals;
    }

    private Set<String> deriveAccountSignals(AccountCatalogEntity candidate) {
        Set<String> signals = new HashSet<>();
        signals.addAll(canonicalizeCategories(candidate.getTags()));
        signals.addAll(extractCategoriesFromText(candidate.getProductName()));
        signals.addAll(extractCategoriesFromText(candidate.getSummary()));
        signals.addAll(extractCategoriesFromText(candidate.getAccountKind()));

        String accountKind = normalize(candidate.getAccountKind());
        if (accountKind.contains("예금") || accountKind.contains("적금")) {
            signals.add("savings");
        }
        if (accountKind.contains("외화")) {
            signals.add("global");
            signals.add("travel");
        }
        if (accountKind.contains("입출금")) {
            signals.add("daily");
        }

        return signals;
    }

    private Set<String> deriveCardCategories(CardCatalogEntity candidate) {
        Set<String> categories = new HashSet<>();

        categories.addAll(canonicalizeCategories(candidate.getCategories()));
        categories.addAll(canonicalizeCategories(candidate.getTags()));
        categories.addAll(extractCategoriesFromText(candidate.getProductName()));
        categories.addAll(extractCategoriesFromText(candidate.getSummary()));

        Set<String> tags = lowerSet(candidate.getTags());
        if (tags.contains("travel") || tags.contains("mileage")) {
            categories.add("travel");
        }
        if (tags.contains("starter") || tags.contains("no-fee") || tags.contains("nofee")) {
            categories.add("starter");
        }
        if (tags.contains("daily") || tags.contains("cashback")) {
            categories.add("daily");
        }

        return categories;
    }

    private Set<String> canonicalizeCategories(Iterable<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) {
            return result;
        }

        for (String value : values) {
            result.addAll(canonicalizeCategoryValue(value));
        }
        return result;
    }

    private Set<String> canonicalizeCategoryValue(String raw) {
        Set<String> result = new HashSet<>();
        String normalized = normalize(raw);
        if (normalized.isBlank()) {
            return result;
        }

        // direct mapping for full token
        String direct = CATEGORY_ALIASES.get(normalizeCategoryToken(normalized));
        if (direct != null) {
            result.add(direct);
        }

        // split mapping (e.g. "온라인/구독")
        for (String part : normalized.split("[,|/\\s]+")) {
            String mapped = CATEGORY_ALIASES.get(normalizeCategoryToken(part));
            if (mapped != null) {
                result.add(mapped);
            }
        }

        // text keyword mapping fallback
        result.addAll(extractCategoriesFromText(normalized));

        return result;
    }

    private Set<String> extractCategoriesFromText(String text) {
        Set<String> result = new HashSet<>();
        String normalizedText = normalizeCategoryToken(text);
        if (normalizedText.isBlank()) {
            return result;
        }

        for (CategoryKeywordRule rule : CATEGORY_KEYWORD_RULES) {
            for (String keyword : rule.keywords()) {
                if (normalizedText.contains(keyword)) {
                    result.add(rule.category());
                    break;
                }
            }
        }

        return result;
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return Set.of();
        }

        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return intersection;
    }

    private boolean hasLifestyleCategory(Set<String> categories) {
        return categories.contains("online")
            || categories.contains("grocery")
            || categories.contains("transport")
            || categories.contains("dining")
            || categories.contains("cafe")
            || categories.contains("subscription")
            || categories.contains("daily");
    }

    private RateInfo extractRateInfo(String summary) {
        if (summary == null || summary.isBlank()) {
            return new RateInfo(null, null);
        }

        Double maxRate = extractRate(summary, MAX_RATE_PATTERN);
        Double baseRate = extractRate(summary, BASE_RATE_PATTERN);

        return new RateInfo(maxRate, baseRate);
    }

    private Double extractRate(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String value = matcher.group(1);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String formatPercent(Double value) {
        if (value == null) {
            return "";
        }

        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private AnnualFeeInfo parseAnnualFee(String annualFeeText) {
        String text = normalize(annualFeeText);
        if (text.isBlank()) {
            return new AnnualFeeInfo(true, null);
        }

        if (text.contains("없음") || text.contains("면제") || text.contains("무료") || text.contains("0원")) {
            return new AnnualFeeInfo(true, 0);
        }

        Matcher manWonMatcher = ANNUAL_FEE_MAN_WON_PATTERN.matcher(text);
        if (manWonMatcher.find()) {
            try {
                double manWon = Double.parseDouble(manWonMatcher.group(1));
                int won = (int) Math.round(manWon * 10000);
                return new AnnualFeeInfo(won <= 0, won);
            } catch (NumberFormatException ignored) {
                // fallback to next parser
            }
        }

        Matcher wonMatcher = ANNUAL_FEE_WON_PATTERN.matcher(text);
        if (wonMatcher.find()) {
            String rawNumber = wonMatcher.group(1).replace(",", "");
            try {
                int won = Integer.parseInt(rawNumber);
                return new AnnualFeeInfo(won <= 0, won);
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }

        return new AnnualFeeInfo(false, null);
    }

    private String summaryHighlight(String summary) {
        String normalized = normalize(summary);
        if (normalized.isBlank()) {
            return "";
        }

        String compact = summary.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 48) {
            return compact;
        }
        return compact.substring(0, 48) + "...";
    }

    private String summarizeCardQuantifiedBenefits(CardCatalogEntity candidate) {
        String summary = normalize(candidate.getSummary());
        if (summary.isBlank()) {
            return "정량 혜택 정보 없음 (공식 페이지에서 할인/적립 한도 확인)";
        }

        List<String> segments = splitTextSegments(candidate.getSummary());
        Set<String> captures = new LinkedHashSet<>();

        for (String segment : segments) {
            if (!containsDigit(segment)) {
                continue;
            }

            boolean hasBenefitKeyword = containsAnyKeyword(normalize(segment), CARD_BENEFIT_KEYWORDS);
            if (hasBenefitKeyword) {
                captures.add(compactSegment(segment));
            }
        }

        Matcher percentMatcher = CARD_PERCENT_PATTERN.matcher(candidate.getSummary());
        while (percentMatcher.find() && captures.size() < 5) {
            captures.add(percentMatcher.group(1) + "%");
        }

        Matcher amountMatcher = CARD_AMOUNT_PATTERN.matcher(candidate.getSummary());
        while (amountMatcher.find() && captures.size() < 5) {
            captures.add(compactSegment(amountMatcher.group(1)));
        }

        if (captures.isEmpty()) {
            return "정량 혜택 정보 없음 (공식 페이지에서 할인/적립 한도 확인)";
        }

        return captures.stream().limit(4).collect(Collectors.joining(" · "));
    }

    private String buildAnnualFeeEstimateText(String annualFeeText) {
        AnnualFeeInfo annualFeeInfo = parseAnnualFee(annualFeeText);
        if (annualFeeInfo.estimatedWon() == null) {
            return "수치 확인 어려움";
        }

        if (annualFeeInfo.estimatedWon() == 0) {
            return "0원 (면제/없음)";
        }

        return annualFeeInfo.estimatedWon() + "원 수준";
    }

    private String normalizeAnnualFeeText(String annualFeeText) {
        String normalized = annualFeeText == null ? "" : annualFeeText.trim();
        if (normalized.isBlank()) {
            return "연회비 정보 없음";
        }

        String lower = normalize(normalized);
        if ("없음".equals(lower)
            || "면제".equals(lower)
            || "무료".equals(lower)
            || "0원".equals(lower)
            || "무연회비".equals(lower)) {
            return "연회비 없음";
        }

        return normalized;
    }

    private List<String> splitTextSegments(String text) {
        String normalized = text == null ? "" : text.replace('\n', ' ');
        String[] tokens = normalized.split("[·;,|]");
        List<String> segments = new ArrayList<>();
        for (String token : tokens) {
            String compact = compactSegment(token);
            if (!compact.isBlank()) {
                segments.add(compact);
            }
        }
        return segments;
    }

    private String compactSegment(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private boolean containsDigit(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyKeyword(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String labelsOf(Set<String> categories) {
        return categories.stream()
            .sorted()
            .map(category -> CATEGORY_LABELS.getOrDefault(category, category))
            .collect(Collectors.joining(", "));
    }

    private List<RankedProduct> assignRank(List<ScoredProduct> scoredProducts) {
        List<RankedProduct> ranked = new ArrayList<>();
        for (int i = 0; i < scoredProducts.size(); i++) {
            ScoredProduct scored = scoredProducts.get(i);
            ranked.add(new RankedProduct(
                i + 1,
                scored.productType,
                scored.productId,
                scored.provider,
                scored.name,
                scored.summary,
                scored.meta,
                scored.score,
                scored.reason,
                scored.minExpectedMonthlyBenefit,
                scored.expectedMonthlyBenefit,
                scored.maxExpectedMonthlyBenefit,
                scored.estimateMethod,
                scored.benefitComponents,
                scored.officialUrl,
                scored.detailFields
            ));
        }
        return ranked;
    }

    private int estimateNetMonthlyProfit(List<RankedProduct> accounts, List<RankedProduct> cards) {
        int totalScore = accounts.stream().mapToInt(RankedProduct::score).sum()
            + cards.stream().mapToInt(RankedProduct::score).sum();
        return totalScore * 120;
    }

    private RecommendationItemEntity toEntity(RecommendationRunEntity run, RankedProduct ranked) {
        return new RecommendationItemEntity(
            run,
            ranked.rank,
            limitLength(ranked.productType, 20),
            limitLength(ranked.productId, 80),
            limitLength(ranked.provider, 80),
            limitLength(ranked.name, 120),
            ranked.summary,
            limitLength(ranked.meta, 120),
            ranked.score,
            limitLength(ranked.reason, 280),
            ranked.officialUrl
        );
    }

    private RecommendationItemResponse toItemResponse(
        RecommendationItemEntity item,
        Map<String, String> officialUrlOverrides
    ) {
        ProductBenefitEstimate benefitEstimate = estimateProductBenefit(
            item.getProductType(),
            item.getScore(),
            List.of(),
            item.getReasonText()
        );

        return new RecommendationItemResponse(
            item.getRank(),
            item.getProductType(),
            item.getProductId(),
            item.getProviderName(),
            item.getProductName(),
            item.getSummary(),
            item.getMeta(),
            item.getScore(),
            item.getReasonText(),
            benefitEstimate.minExpectedMonthlyBenefit(),
            benefitEstimate.expectedMonthlyBenefit(),
            benefitEstimate.maxExpectedMonthlyBenefit(),
            benefitEstimate.estimateMethod(),
            benefitEstimate.benefitComponents(),
            resolveDetailFields(item, officialUrlOverrides)
        );
    }

    private List<RecommendationItemResponse> toItemResponses(List<RankedProduct> rankedProducts) {
        return rankedProducts.stream()
            .map(item -> new RecommendationItemResponse(
                item.rank,
                item.productType,
                item.productId,
                item.provider,
                item.name,
                item.summary,
                item.meta,
                item.score,
                item.reason,
                item.minExpectedMonthlyBenefit,
                item.expectedMonthlyBenefit,
                item.maxExpectedMonthlyBenefit,
                item.estimateMethod,
                item.benefitComponents,
                item.detailFields
            ))
            .toList();
    }

    private List<RecommendationDetailFieldResponse> resolveDetailFields(
        RecommendationItemEntity item,
        Map<String, String> officialUrlOverrides
    ) {
        if ("ACCOUNT".equals(item.getProductType())) {
            return accountCatalogRepository.findByProductKey(item.getProductId())
                .map(candidate -> buildAccountDetailFields(
                    candidate,
                    resolveOfficialUrlForProduct(
                        candidate.getProductKey(),
                        "ACCOUNT",
                        candidate.getProviderName(),
                        candidate.getProductName(),
                        candidate.getOfficialUrl(),
                        officialUrlOverrides
                    )
                ))
                .orElseGet(() -> buildFallbackDetailFields(item, officialUrlOverrides));
        }

        if ("CARD".equals(item.getProductType())) {
            return cardCatalogRepository.findByProductKey(item.getProductId())
                .map(candidate -> buildCardDetailFields(
                    candidate,
                    resolveOfficialUrlForProduct(
                        candidate.getProductKey(),
                        "CARD",
                        candidate.getProviderName(),
                        candidate.getProductName(),
                        candidate.getOfficialUrl(),
                        officialUrlOverrides
                    )
                ))
                .orElseGet(() -> buildFallbackDetailFields(item, officialUrlOverrides));
        }

        return buildFallbackDetailFields(item, officialUrlOverrides);
    }

    private List<RecommendationDetailFieldResponse> buildAccountDetailFields(
        AccountCatalogEntity candidate,
        String officialUrl
    ) {
        List<RecommendationDetailFieldResponse> fields = new ArrayList<>();
        OfficialLinkPlan linkPlan = resolveOfficialLinkPlan(
            "ACCOUNT",
            candidate.getProviderName(),
            candidate.getProductName(),
            officialUrl
        );

        addDetailField(fields, "상품명", candidate.getProductName());
        addDetailField(fields, "상품유형", candidate.getAccountKind() + " 계좌");
        addDetailField(fields, "가입대상", inferAccountEligibility(candidate));
        addDetailField(fields, "핵심 설명", candidate.getSummary());

        Set<String> tagSignals = canonicalizeCategories(candidate.getTags());
        if (!tagSignals.isEmpty()) {
            addDetailField(fields, "핵심 태그", labelsOf(tagSignals));
        }

        appendOfficialLinkFields(fields, linkPlan);
        return fields;
    }

    private List<RecommendationDetailFieldResponse> buildCardDetailFields(
        CardCatalogEntity candidate,
        String officialUrl
    ) {
        List<RecommendationDetailFieldResponse> fields = new ArrayList<>();
        OfficialLinkPlan linkPlan = resolveOfficialLinkPlan(
            "CARD",
            candidate.getProviderName(),
            candidate.getProductName(),
            officialUrl
        );

        addDetailField(fields, "상품명", candidate.getProductName());
        String normalizedAnnualFeeText = normalizeAnnualFeeText(candidate.getAnnualFeeText());
        addDetailField(fields, "연회비", normalizedAnnualFeeText);
        addDetailField(fields, "연회비(추정)", buildAnnualFeeEstimateText(normalizedAnnualFeeText));
        addDetailField(fields, "가입대상", inferCardEligibility(candidate));
        addDetailField(fields, "핵심 혜택", candidate.getSummary());
        addDetailField(fields, "정량 혜택", summarizeCardQuantifiedBenefits(candidate));

        Set<String> categories = deriveCardCategories(candidate);
        if (!categories.isEmpty()) {
            addDetailField(fields, "혜택 카테고리", labelsOf(categories));
        }

        Set<String> tagSignals = canonicalizeCategories(candidate.getTags());
        if (!tagSignals.isEmpty()) {
            addDetailField(fields, "핵심 태그", labelsOf(tagSignals));
        }

        appendOfficialLinkFields(fields, linkPlan);
        return fields;
    }

    private List<RecommendationDetailFieldResponse> buildFallbackDetailFields(
        RecommendationItemEntity item,
        Map<String, String> officialUrlOverrides
    ) {
        List<RecommendationDetailFieldResponse> fields = new ArrayList<>();
        String resolvedOfficialUrl = resolveOfficialUrlForProduct(
            item.getProductId(),
            item.getProductType(),
            item.getProviderName(),
            item.getProductName(),
            item.getOfficialUrl(),
            officialUrlOverrides
        );
        OfficialLinkPlan linkPlan = resolveOfficialLinkPlan(
            item.getProductType(),
            item.getProviderName(),
            item.getProductName(),
            resolvedOfficialUrl
        );
        addDetailField(fields, "상품명", item.getProductName());
        addDetailField(fields, "요약", item.getSummary());
        addDetailField(fields, "참고", item.getMeta());
        appendOfficialLinkFields(fields, linkPlan);
        return fields;
    }

    private void appendOfficialLinkFields(
        List<RecommendationDetailFieldResponse> fields,
        OfficialLinkPlan linkPlan
    ) {
        addDetailField(fields, "공식 설명서/상세", linkPlan.redirectUrl(), true);
    }

    private String resolveOfficialUrlForProduct(
        String productKey,
        String productType,
        String providerName,
        String productName,
        String officialUrl,
        Map<String, String> officialUrlOverrides
    ) {
        return productUrlOverrideService.resolveOfficialUrl(
            productKey,
            productType,
            providerName,
            productName,
            officialUrl,
            officialUrlOverrides
        );
    }

    private OfficialLinkPlan resolveOfficialLinkPlan(
        String productType,
        String providerName,
        String productName,
        String officialUrl
    ) {
        String normalizedUrl = normalizeOfficialUrl(officialUrl);
        if (normalizedUrl.isBlank()) {
            return new OfficialLinkPlan("", "공식 링크 미제공", "", "");
        }

        if (!isLikelyGenericOfficialUrl(normalizedUrl)) {
            return new OfficialLinkPlan(normalizedUrl, "공식 상품 상세 링크", normalizedUrl, "");
        }

        return new OfficialLinkPlan(normalizedUrl, "공식 홈페이지/목록 링크", normalizedUrl, "");
    }

    private boolean isLikelyGenericOfficialUrl(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String host = normalize(uri.getHost());
            String path = normalize(uri.getPath());
            String query = normalize(uri.getQuery());

            if (host.isBlank()) {
                return true;
            }

            if (query.contains("prd") || query.contains("product") || query.contains("code=") || query.contains("id=")) {
                return false;
            }

            if ("/".equals(path) || path.isBlank()) {
                return true;
            }

            if (host.contains("epostbank.go.kr") && path.contains("cdcf")) {
                return true;
            }

            if (host.contains("kdb.co.kr") && ("/".equals(path) || path.contains("/main"))) {
                return true;
            }

            if (host.contains("fsc.go.kr") || host.contains("finlife.fss.or.kr")) {
                return true;
            }

            int segmentCount = 0;
            for (String segment : path.split("/")) {
                if (!segment.isBlank()) {
                    segmentCount++;
                }
            }
            return segmentCount <= 1 && query.isBlank();
        } catch (URISyntaxException exception) {
            return true;
        }
    }


    private String normalizeOfficialUrl(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }

    private String inferAccountEligibility(AccountCatalogEntity candidate) {
        String text = normalize(candidate.getProductName() + " " + candidate.getSummary() + " " + candidate.getAccountKind());
        if (text.contains("청년") || text.contains("young")) {
            return "청년/사회초년생 우대 가능";
        }
        if (text.contains("법인") || text.contains("기업")) {
            return "개인·법인 구분형 (세부 조건은 공식 페이지 확인)";
        }
        return "개인 고객 중심 (세부 조건은 공식 페이지 확인)";
    }

    private String inferCardEligibility(CardCatalogEntity candidate) {
        String text = normalize(candidate.getProductName() + " " + candidate.getSummary());
        if (text.contains("법인")) {
            return "개인/법인 구분형 (세부 조건은 공식 페이지 확인)";
        }
        if (text.contains("개인")) {
            return "개인 고객";
        }
        return "개인 고객 중심 (발급 조건은 공식 페이지 확인)";
    }

    private void addDetailField(List<RecommendationDetailFieldResponse> fields, String label, String value) {
        addDetailField(fields, label, value, false);
    }

    private void addDetailField(List<RecommendationDetailFieldResponse> fields, String label, String value, boolean link) {
        String normalizedLabel = label == null ? "" : label.trim();
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedLabel.isBlank() || normalizedValue.isBlank()) {
            return;
        }
        fields.add(new RecommendationDetailFieldResponse(normalizedLabel, normalizedValue, link));
    }

    private Set<String> lowerSet(Iterable<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private String normalizePriority(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "cashback", "캐시백", "할인", "생활" -> "cashback";
            case "savings", "저축", "금리", "rate" -> "savings";
            case "travel", "여행", "해외" -> "travel";
            case "starter", "초보", "저비용" -> "starter";
            case "salary", "급여", "주거래" -> "salary";
            case "annualfee", "연회비", "fee" -> "annualfee";
            default -> normalized;
        };
    }

    private String resolveAccountPriority(SimulateRecommendationRequest request) {
        String accountPriority = normalizePriority(request.accountPriority());
        if (!accountPriority.isBlank()) {
            if ("annualfee".equals(accountPriority)) {
                return "starter";
            }
            return accountPriority;
        }

        String fallback = normalizePriority(request.priority());
        if ("annualfee".equals(fallback)) {
            return "starter";
        }
        return fallback;
    }

    private String resolveCardPriority(SimulateRecommendationRequest request) {
        String cardPriority = normalizePriority(request.cardPriority());
        if (!cardPriority.isBlank()) {
            if ("salary".equals(cardPriority)) {
                return "cashback";
            }
            return cardPriority;
        }

        String fallback = normalizePriority(request.priority());
        if ("salary".equals(fallback)) {
            return "cashback";
        }
        return fallback;
    }

    private String normalizeCategoryToken(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[\\s_./|-]+", "")
            .replaceAll("[^a-z0-9가-힣]", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String limitLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }

        if (maxLength <= 3) {
            return trimmed.substring(0, maxLength);
        }

        return trimmed.substring(0, maxLength - 3) + "...";
    }

    private String joinReasons(List<String> reasons) {
        return joinReasons(reasons, 4, " · ");
    }

    private String joinReasons(List<String> reasons, int limit, String delimiter) {
        if (reasons.isEmpty()) {
            return "";
        }

        List<String> deduplicated = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String reason : reasons) {
            String normalized = normalize(reason);
            if (!normalized.isBlank() && seen.add(normalized)) {
                deduplicated.add(reason);
            }
            if (deduplicated.size() >= Math.max(limit, 1)) {
                break;
            }
        }

        return String.join(delimiter, deduplicated);
    }

    private void addScorePart(List<ScoreReasonPart> scoreParts, String label, int points) {
        if (points == 0) {
            return;
        }
        scoreParts.add(new ScoreReasonPart(label, points));
    }

    private String buildReasonWithScore(
        List<ScoreReasonPart> scoreParts,
        List<String> coreReasons,
        int totalScore,
        String tieBreakRule
    ) {
        List<String> lines = new ArrayList<>();

        String coreReasonLine = joinReasons(coreReasons, 6, " · ");
        if (!coreReasonLine.isBlank()) {
            lines.add("핵심근거: " + coreReasonLine);
        }

        if (!normalize(tieBreakRule).isBlank()) {
            lines.add("동점처리: " + tieBreakRule);
        }

        return String.join("\n", lines);
    }

    private String formatSignedPoints(int points) {
        return points >= 0 ? "+" + points : String.valueOf(points);
    }

    private String extractCoreReason(String reasonText) {
        if (reasonText == null || reasonText.isBlank()) {
            return "기본 조건 기반 추천";
        }

        for (String line : reasonText.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("핵심근거:")) {
                String extracted = trimmed.substring("핵심근거:".length()).trim();
                if (!extracted.isBlank()) {
                    return extracted;
                }
            }
        }

        return reasonText.replaceAll("\\s+", " ").trim();
    }


    private ProductBenefitEstimate estimateProductBenefit(
        String productType,
        int totalScore,
        List<ScoreReasonPart> scoreParts,
        String reasonText
    ) {
        int pointUnitWon = "ACCOUNT".equals(productType) ? 130 : 120;
        List<ScoreReasonPart> resolvedParts = scoreParts == null || scoreParts.isEmpty()
            ? parseScorePartsFromReason(reasonText, totalScore)
            : scoreParts;

        List<RecommendationBundleBenefitComponentResponse> components = new ArrayList<>();
        for (int index = 0; index < resolvedParts.size(); index++) {
            ScoreReasonPart part = resolvedParts.get(index);
            int amount = part.points() * pointUnitWon;
            String key = productType.toLowerCase(Locale.ROOT) + "_score_" + index;
            String labelPrefix = "ACCOUNT".equals(productType) ? "계좌" : "카드";
            String partLabel = part.label() == null ? "" : part.label().trim();
            String label = "기본점수".equals(partLabel)
                ? labelPrefix + " 기본 절감액"
                : labelPrefix + ": " + partLabel;
            String condition = part.points() >= 0 ? "조건 충족 시 반영" : "비용/패널티 반영";
            components.add(new RecommendationBundleBenefitComponentResponse(
                key,
                label,
                condition,
                amount,
                true
            ));
        }

        int expected = components.stream()
            .mapToInt(RecommendationBundleBenefitComponentResponse::amountWonPerMonth)
            .sum();
        expected = Math.max(0, expected);

        int min = Math.max(0, (int) Math.round(expected * 0.72d));
        int max = Math.max(expected, (int) Math.round(expected * 1.18d));

        String estimateMethod = "점수 환산 기반 추정 (1점당 " + pointUnitWon + "원, 항목별 가감점 합산)";

        return new ProductBenefitEstimate(min, expected, max, estimateMethod, components);
    }

    private List<ScoreReasonPart> parseScorePartsFromReason(String reasonText, int fallbackScore) {
        if (reasonText == null || reasonText.isBlank()) {
            return List.of(new ScoreReasonPart("기본점수", fallbackScore));
        }

        String scoreLine = null;
        for (String rawLine : reasonText.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("점수구성:")) {
                scoreLine = line.substring("점수구성:".length()).trim();
                break;
            }
        }

        if (scoreLine == null || scoreLine.isBlank()) {
            return List.of(new ScoreReasonPart("기본점수", fallbackScore));
        }

        scoreLine = scoreLine.replaceAll("\\s*=\\s*[-+]?\\d+\\s*점\\s*$", "").trim();
        if (scoreLine.isBlank()) {
            return List.of(new ScoreReasonPart("기본점수", fallbackScore));
        }

        List<ScoreReasonPart> parts = new ArrayList<>();
        for (String token : scoreLine.split(",")) {
            String item = token.trim();
            Matcher matcher = Pattern.compile("(.+)\\(([+-]?\\d+)점\\)$").matcher(item);
            if (matcher.find()) {
                String label = matcher.group(1).trim();
                int points;
                try {
                    points = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException exception) {
                    continue;
                }
                parts.add(new ScoreReasonPart(label, points));
            }
        }

        if (parts.isEmpty()) {
            parts.add(new ScoreReasonPart("기본점수", fallbackScore));
        }

        return parts;
    }


    private static Map<String, String> buildCategoryAliases() {
        Map<String, String> aliases = new HashMap<>();

        putAlias(aliases, "online", "online", "ecommerce", "shopping", "쇼핑", "온라인", "간편결제", "pay", "모바일결제");
        putAlias(aliases, "grocery", "grocery", "mart", "supermarket", "장보기", "마트", "식자재");
        putAlias(aliases, "transport", "transport", "traffic", "transit", "mobility", "교통", "지하철", "버스", "택시", "주유", "모빌리티");
        putAlias(aliases, "dining", "dining", "food", "restaurant", "외식", "식당", "배달", "푸드");
        putAlias(aliases, "cafe", "cafe", "coffee", "카페", "커피");
        putAlias(aliases, "subscription", "subscription", "sub", "ott", "streaming", "구독", "스트리밍");
        putAlias(aliases, "travel", "travel", "trip", "airline", "hotel", "여행", "해외", "항공", "숙박");
        putAlias(aliases, "salary", "salary", "급여", "월급", "급여이체");
        putAlias(aliases, "savings", "savings", "saving", "save", "저축", "금리", "예금", "적금");
        putAlias(aliases, "starter", "starter", "beginner", "초보", "저비용", "무연회비");
        putAlias(aliases, "daily", "daily", "생활", "일상", "cashback", "할인");
        putAlias(aliases, "global", "global", "외화", "글로벌");

        return aliases;
    }

    private static Map<String, String> buildCategoryLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("online", "온라인쇼핑");
        labels.put("grocery", "장보기/마트");
        labels.put("transport", "교통/모빌리티");
        labels.put("dining", "외식");
        labels.put("cafe", "카페");
        labels.put("subscription", "구독");
        labels.put("travel", "여행/해외");
        labels.put("salary", "급여/이체");
        labels.put("savings", "저축/금리");
        labels.put("starter", "초보자/저비용");
        labels.put("daily", "생활소비");
        labels.put("global", "외화/글로벌");
        return labels;
    }

    private static List<CategoryKeywordRule> buildCategoryKeywordRules() {
        return List.of(
            new CategoryKeywordRule("online", Set.of("온라인", "쇼핑", "간편결제", "ecommerce", "shopping", "오픈마켓")),
            new CategoryKeywordRule("grocery", Set.of("마트", "장보기", "슈퍼", "식자재", "생필품")),
            new CategoryKeywordRule("transport", Set.of("교통", "지하철", "버스", "택시", "주유", "모빌리티")),
            new CategoryKeywordRule("dining", Set.of("외식", "식당", "배달", "푸드", "레스토랑")),
            new CategoryKeywordRule("cafe", Set.of("카페", "커피")),
            new CategoryKeywordRule("subscription", Set.of("구독", "ott", "스트리밍", "멤버십")),
            new CategoryKeywordRule("travel", Set.of("여행", "해외", "항공", "마일", "숙박")),
            new CategoryKeywordRule("salary", Set.of("급여", "월급", "급여이체")),
            new CategoryKeywordRule("savings", Set.of("저축", "금리", "적금", "예금", "복리", "우대금리")),
            new CategoryKeywordRule("starter", Set.of("초보", "무연회비", "저비용", "신규")),
            new CategoryKeywordRule("daily", Set.of("생활", "일상", "캐시백", "할인")),
            new CategoryKeywordRule("global", Set.of("외화", "글로벌", "환전"))
        );
    }

    private static void putAlias(Map<String, String> aliases, String canonical, String... variants) {
        for (String variant : variants) {
            aliases.put(normalizeStaticCategoryToken(variant), canonical);
        }
    }

    private static String normalizeStaticCategoryToken(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[\\s_./|-]+", "")
            .replaceAll("[^a-z0-9가-힣]", "");
    }

    private record ScoredProduct(
        String productType,
        String productId,
        String provider,
        String name,
        String summary,
        String meta,
        int score,
        String reason,
        int minExpectedMonthlyBenefit,
        int expectedMonthlyBenefit,
        int maxExpectedMonthlyBenefit,
        String estimateMethod,
        List<RecommendationBundleBenefitComponentResponse> benefitComponents,
        String officialUrl,
        List<RecommendationDetailFieldResponse> detailFields
    ) {
    }

    private record RankedProduct(
        int rank,
        String productType,
        String productId,
        String provider,
        String name,
        String summary,
        String meta,
        int score,
        String reason,
        int minExpectedMonthlyBenefit,
        int expectedMonthlyBenefit,
        int maxExpectedMonthlyBenefit,
        String estimateMethod,
        List<RecommendationBundleBenefitComponentResponse> benefitComponents,
        String officialUrl,
        List<RecommendationDetailFieldResponse> detailFields
    ) {
    }

    private record BundleCandidate(
        int rank,
        String title,
        String accountProductId,
        String accountLabel,
        String cardProductId,
        String cardLabel,
        int minExtraMonthlyBenefit,
        int expectedExtraMonthlyBenefit,
        int maxExtraMonthlyBenefit,
        int accountExpectedExtraMonthlyBenefit,
        int cardExpectedExtraMonthlyBenefit,
        int synergyExtraMonthlyBenefit,
        String estimateMethod,
        List<RecommendationBundleBenefitComponentResponse> benefitComponents,
        String reason
    ) {
    }

    private record BundleBenefitEstimate(
        int minExtraMonthlyBenefit,
        int expectedExtraMonthlyBenefit,
        int maxExtraMonthlyBenefit,
        int totalSynergyBonus,
        int accountExpectedExtraMonthlyBenefit,
        int cardExpectedExtraMonthlyBenefit,
        int synergyExtraMonthlyBenefit,
        String estimateMethod,
        List<RecommendationBundleBenefitComponentResponse> benefitComponents
    ) {
    }

    private record ProductBenefitEstimate(
        int minExpectedMonthlyBenefit,
        int expectedMonthlyBenefit,
        int maxExpectedMonthlyBenefit,
        String estimateMethod,
        List<RecommendationBundleBenefitComponentResponse> benefitComponents
    ) {
    }

    private record ScoreReasonPart(String label, int points) {
    }

    private record CategoryKeywordRule(String category, Set<String> keywords) {

        private CategoryKeywordRule {
            Set<String> normalizedKeywords = keywords.stream()
                .map(RecommendationService::normalizeStaticCategoryToken)
                .collect(Collectors.toSet());
            keywords = normalizedKeywords;
        }
    }

    private record RateInfo(Double maxRate, Double baseRate) {
    }

    private record AnnualFeeInfo(boolean lowFee, Integer estimatedWon) {
    }

    private record OfficialLinkPlan(
        String redirectUrl,
        String linkTypeLabel,
        String originalUrl,
        String searchUrl
    ) {
    }
}
