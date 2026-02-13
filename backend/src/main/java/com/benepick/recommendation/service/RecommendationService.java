package com.benepick.recommendation.service;

import com.benepick.recommendation.dto.RecommendationBundleResponse;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    private static final Map<String, String> CATEGORY_ALIASES = buildCategoryAliases();
    private static final Map<String, String> CATEGORY_LABELS = buildCategoryLabels();
    private static final List<CategoryKeywordRule> CATEGORY_KEYWORD_RULES = buildCategoryKeywordRules();

    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final RecommendationRedirectEventRepository recommendationRedirectEventRepository;
    private final AccountCatalogRepository accountCatalogRepository;
    private final CardCatalogRepository cardCatalogRepository;
    private final RecommendationScoringProperties scoringProperties;

    public RecommendationService(
        RecommendationRunRepository recommendationRunRepository,
        RecommendationItemRepository recommendationItemRepository,
        RecommendationRedirectEventRepository recommendationRedirectEventRepository,
        AccountCatalogRepository accountCatalogRepository,
        CardCatalogRepository cardCatalogRepository,
        RecommendationScoringProperties scoringProperties
    ) {
        this.recommendationRunRepository = recommendationRunRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.recommendationRedirectEventRepository = recommendationRedirectEventRepository;
        this.accountCatalogRepository = accountCatalogRepository;
        this.cardCatalogRepository = cardCatalogRepository;
        this.scoringProperties = scoringProperties;
    }

    @Transactional
    public RecommendationRunResponse simulate(SimulateRecommendationRequest request) {
        List<RankedProduct> rankedAccounts = rankAccounts(request);
        List<RankedProduct> rankedCards = rankCards(request);

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
            buildBundles(accounts, cards)
        );
    }

    @Transactional(readOnly = true)
    public RecommendationRunResponse getRun(UUID runId) {
        RecommendationRunEntity run = recommendationRunRepository
            .findById(runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation run not found"));

        List<RecommendationItemEntity> items = recommendationItemRepository
            .findByRecommendationRun_IdOrderByProductTypeAscRankAsc(runId);

        List<RecommendationItemResponse> accounts = items.stream()
            .filter(item -> "ACCOUNT".equals(item.getProductType()))
            .sorted(Comparator.comparingInt(RecommendationItemEntity::getRank))
            .map(this::toItemResponse)
            .toList();

        List<RecommendationItemResponse> cards = items.stream()
            .filter(item -> "CARD".equals(item.getProductType()))
            .sorted(Comparator.comparingInt(RecommendationItemEntity::getRank))
            .map(this::toItemResponse)
            .toList();

        return new RecommendationRunResponse(
            run.getId(),
            normalize(run.getPriority()),
            run.getExpectedNetMonthlyProfit(),
            accounts,
            cards,
            buildBundles(accounts, cards)
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

        RecommendationRedirectEventEntity event = new RecommendationRedirectEventEntity(
            runId,
            normalizedType,
            request.productId(),
            item.getOfficialUrl(),
            userAgent,
            ipAddress,
            referrer
        );
        recommendationRedirectEventRepository.save(event);

        return new RecommendationRedirectResponse(item.getOfficialUrl());
    }

    private List<RankedProduct> rankAccounts(SimulateRecommendationRequest request) {
        String priority = normalizePriority(request.priority());
        String salaryTransfer = normalize(request.salaryTransfer());
        String travelLevel = normalize(request.travelLevel());
        Set<String> userCategories = canonicalizeCategories(request.categories());
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

                Set<String> accountSignals = deriveAccountSignals(candidate);
                Set<String> matchedIntentSignals = intersection(accountSignals, accountIntentSignals);

                RateInfo rateInfo = extractRateInfo(candidate.getSummary());
                if (rateInfo.maxRate() != null) {
                    reasons.add("최고 금리 " + formatPercent(rateInfo.maxRate()) + "% (상품 요약 기준)");
                    if (rateInfo.maxRate() >= accountScore.getHighRateThreshold()) {
                        score += accountScore.getHighRateBonusWeight();
                    }
                }
                if (rateInfo.baseRate() != null) {
                    reasons.add("기본 금리 " + formatPercent(rateInfo.baseRate()) + "% 확인");
                }

                if ("yes".equals(salaryTransfer) && accountSignals.contains("salary")) {
                    score += accountScore.getSalaryTransferWeight();
                    reasons.add("급여이체 조건 충족 시 우대 혜택 가능");
                }

                switch (priority) {
                    case "savings" -> {
                        if (accountSignals.contains("savings")) {
                            score += accountScore.getPrioritySavingsWeight();
                            reasons.add("저축/금리 우선순위와 상품 성격 일치");
                        }
                    }
                    case "starter" -> {
                        if (accountSignals.contains("starter")) {
                            score += accountScore.getPriorityStarterWeight();
                            reasons.add("초기 이용자 친화 조건과 일치");
                        }
                    }
                    case "travel" -> {
                        if (accountSignals.contains("travel") || accountSignals.contains("global")) {
                            score += accountScore.getPriorityTravelWeight();
                            reasons.add("여행/외화 중심 우선순위 반영");
                        }
                    }
                    case "cashback" -> {
                        if (accountSignals.contains("daily") || accountSignals.contains("salary")) {
                            score += accountScore.getPriorityCashbackWeight();
                            reasons.add("생활소비 연동형 계좌 조건과 맞음");
                        }
                    }
                    default -> {
                    }
                }

                if ("often".equals(travelLevel)
                    && (accountSignals.contains("global") || accountSignals.contains("travel"))) {
                    score += accountScore.getTravelOftenGlobalWeight();
                    reasons.add("해외 이용 빈도에 적합한 신호 확인");
                }

                if (request.age() <= accountScore.getYoungAgeMax() && accountSignals.contains("starter")) {
                    score += accountScore.getYoungWeight();
                    reasons.add("연령 구간에 맞는 우대/간편형 조건");
                }

                if (request.monthlySpend() >= accountScore.getDailySpendThreshold() && accountSignals.contains("daily")) {
                    score += accountScore.getDailySpendWeight();
                    reasons.add("생활비 흐름과 연결되는 계좌 패턴");
                }

                if (!matchedIntentSignals.isEmpty()) {
                    score += matchedIntentSignals.size() * accountScore.getIntentCategoryHitWeight();
                    reasons.add("일치 신호: " + labelsOf(matchedIntentSignals));
                }

                return new ScoredProduct(
                    "ACCOUNT",
                    candidate.getProductKey(),
                    candidate.getProviderName(),
                    candidate.getProductName(),
                    candidate.getSummary(),
                    candidate.getAccountKind() + " 계좌",
                    Math.max(0, score),
                    joinReasons(reasons),
                    candidate.getOfficialUrl()
                );
            })
            .sorted(Comparator.comparingInt(ScoredProduct::score).reversed())
            .limit(3)
            .toList();

        return assignRank(scored);
    }

    private List<RankedProduct> rankCards(SimulateRecommendationRequest request) {
        String priority = normalizePriority(request.priority());
        String travelLevel = normalize(request.travelLevel());
        Set<String> userCategories = canonicalizeCategories(request.categories());

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

                Set<String> tagSignals = canonicalizeCategories(candidate.getTags());
                Set<String> cardCategories = deriveCardCategories(candidate);
                Set<String> matchedCategories = intersection(cardCategories, userCategories);

                int categoryHit = matchedCategories.size();
                if (categoryHit > 0) {
                    score += categoryHit * cardScore.getCategoryHitWeight();
                    reasons.add("소비 카테고리 일치: " + labelsOf(matchedCategories));
                }

                switch (priority) {
                    case "cashback" -> {
                        if (tagSignals.contains("daily") || tagSignals.contains("online")) {
                            score += cardScore.getPriorityCashbackWeight();
                            reasons.add("생활 할인/캐시백 우선순위와 일치");
                        }
                    }
                    case "travel" -> {
                        if (cardCategories.contains("travel") || tagSignals.contains("travel")) {
                            score += cardScore.getPriorityTravelWeight();
                            reasons.add("여행/해외결제 우선순위 반영");
                        }
                    }
                    case "starter" -> {
                        if (cardCategories.contains("starter") || tagSignals.contains("starter")) {
                            score += cardScore.getPriorityStarterWeight();
                            reasons.add("연회비 부담 최소 선호와 일치");
                        }
                    }
                    case "savings" -> {
                        if (cardCategories.contains("daily") || tagSignals.contains("online")) {
                            score += cardScore.getPrioritySavingsWeight();
                            reasons.add("저축 우선순위에 맞는 고정비/생활비 절감형");
                        }
                    }
                    default -> {
                    }
                }

                if ("often".equals(travelLevel)
                    && (cardCategories.contains("travel") || tagSignals.contains("travel"))) {
                    score += cardScore.getTravelOftenWeight();
                    reasons.add("해외 이용 빈도에 유리한 혜택 구성");
                }

                if (request.monthlySpend() >= cardScore.getDailySpendThreshold()
                    && (tagSignals.contains("daily") || hasLifestyleCategory(cardCategories))) {
                    score += cardScore.getDailySpendWeight();
                    reasons.add("전월 실적 달성 가능성이 높은 소비 패턴");
                }

                AnnualFeeInfo annualFeeInfo = parseAnnualFee(candidate.getAnnualFeeText());
                if (annualFeeInfo.lowFee()) {
                    score += cardScore.getLowAnnualFeeBonusWeight();
                    reasons.add("연회비 부담이 낮음 (" + candidate.getAnnualFeeText() + ")");
                } else if (annualFeeInfo.estimatedWon() != null
                    && annualFeeInfo.estimatedWon() >= cardScore.getHighAnnualFeeThresholdWon()) {
                    score -= cardScore.getHighAnnualFeePenaltyWeight();
                    reasons.add("연회비 수준 고려 필요 (" + candidate.getAnnualFeeText() + ")");
                } else {
                    reasons.add("연회비 정보 반영 (" + candidate.getAnnualFeeText() + ")");
                }

                String summaryHighlight = summaryHighlight(candidate.getSummary());
                if (!summaryHighlight.isBlank()) {
                    reasons.add("핵심 혜택: " + summaryHighlight);
                }

                return new ScoredProduct(
                    "CARD",
                    candidate.getProductKey(),
                    candidate.getProviderName(),
                    candidate.getProductName(),
                    candidate.getSummary(),
                    candidate.getAnnualFeeText(),
                    Math.max(0, score),
                    joinReasons(reasons),
                    candidate.getOfficialUrl()
                );
            })
            .sorted(Comparator.comparingInt(ScoredProduct::score).reversed())
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
                bundle.expectedExtraMonthlyBenefit(),
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

        int synergyBonus = calculateSynergyBonus(account, card);
        int expectedExtraMonthlyBenefit = Math.max(
            6000,
            ((account.score() + card.score()) * 42) + synergyBonus
        );

        bundles.add(new BundleCandidate(
            bundles.size() + 1,
            title,
            account.productId(),
            account.provider() + " · " + account.name(),
            card.productId(),
            card.provider() + " · " + card.name(),
            expectedExtraMonthlyBenefit,
            buildBundleReason(account, card, synergyBonus)
        ));
    }

    private int calculateSynergyBonus(RecommendationItemResponse account, RecommendationItemResponse card) {
        String accountText = normalize(account.summary() + " " + account.reason() + " " + account.meta());
        String cardText = normalize(card.summary() + " " + card.reason() + " " + card.meta());

        int bonus = 0;
        if (accountText.contains("급여")) {
            bonus += 5200;
        }
        if (accountText.contains("저축") || accountText.contains("금리")) {
            bonus += 3600;
        }
        if (cardText.contains("전월") || cardText.contains("실적")) {
            bonus += 4200;
        }
        if (cardText.contains("카테고리") || cardText.contains("생활")) {
            bonus += 3200;
        }
        if ((cardText.contains("여행") || cardText.contains("해외")) && accountText.contains("외화")) {
            bonus += 2800;
        }

        return bonus;
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

        reasons.add("추천 사유 결합: " + account.reason() + " + " + card.reason());
        return joinReasons(reasons);
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
                scored.officialUrl
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
            ranked.productType,
            ranked.productId,
            ranked.provider,
            ranked.name,
            ranked.summary,
            ranked.meta,
            ranked.score,
            ranked.reason,
            ranked.officialUrl
        );
    }

    private RecommendationItemResponse toItemResponse(RecommendationItemEntity item) {
        return new RecommendationItemResponse(
            item.getRank(),
            item.getProductType(),
            item.getProductId(),
            item.getProviderName(),
            item.getProductName(),
            item.getSummary(),
            item.getMeta(),
            item.getScore(),
            item.getReasonText()
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
                item.reason
            ))
            .toList();
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
            case "savings", "저축", "금리" -> "savings";
            case "travel", "여행", "해외" -> "travel";
            case "starter", "초보", "저비용" -> "starter";
            default -> normalized;
        };
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

    private String joinReasons(List<String> reasons) {
        if (reasons.isEmpty()) {
            return "기본 조건 기반 추천";
        }

        List<String> deduplicated = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String reason : reasons) {
            String normalized = normalize(reason);
            if (!normalized.isBlank() && seen.add(normalized)) {
                deduplicated.add(reason);
            }
            if (deduplicated.size() >= 4) {
                break;
            }
        }

        return String.join(" · ", deduplicated);
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
        String officialUrl
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
        String officialUrl
    ) {
    }

    private record BundleCandidate(
        int rank,
        String title,
        String accountProductId,
        String accountLabel,
        String cardProductId,
        String cardLabel,
        int expectedExtraMonthlyBenefit,
        String reason
    ) {
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
}
