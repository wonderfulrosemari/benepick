package com.benepick.recommendation.service;

import com.benepick.recommendation.dto.RecommendationBundleResponse;
import com.benepick.recommendation.dto.RecommendationItemResponse;
import com.benepick.recommendation.dto.RecommendationRedirectRequest;
import com.benepick.recommendation.dto.RecommendationRedirectResponse;
import com.benepick.recommendation.dto.RecommendationRunResponse;
import com.benepick.recommendation.dto.RecommendationRunHistoryItemResponse;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationService {

    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final RecommendationRedirectEventRepository recommendationRedirectEventRepository;
    private final AccountCatalogRepository accountCatalogRepository;
    private final CardCatalogRepository cardCatalogRepository;

    public RecommendationService(
        RecommendationRunRepository recommendationRunRepository,
        RecommendationItemRepository recommendationItemRepository,
        RecommendationRedirectEventRepository recommendationRedirectEventRepository,
        AccountCatalogRepository accountCatalogRepository,
        CardCatalogRepository cardCatalogRepository
    ) {
        this.recommendationRunRepository = recommendationRunRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.recommendationRedirectEventRepository = recommendationRedirectEventRepository;
        this.accountCatalogRepository = accountCatalogRepository;
        this.cardCatalogRepository = cardCatalogRepository;
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
        String priority = normalize(request.priority());
        String salaryTransfer = normalize(request.salaryTransfer());
        String travelLevel = normalize(request.travelLevel());

        List<AccountCatalogEntity> candidates = accountCatalogRepository.findByActiveTrue();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account catalog is empty");
        }

        List<ScoredProduct> scored = candidates.stream()
            .map(candidate -> {
                int score = 45;
                List<String> reasons = new ArrayList<>();
                Set<String> tags = lowerSet(candidate.getTags());

                if ("yes".equals(salaryTransfer) && tags.contains("salary")) {
                    score += 30;
                    reasons.add("급여이체 조건에서 우대 혜택이 큼");
                }
                if ("savings".equals(priority) && tags.contains("savings")) {
                    score += 34;
                    reasons.add("저축/금리 우선순위와 일치");
                }
                if ("starter".equals(priority) && tags.contains("starter")) {
                    score += 24;
                    reasons.add("초보자에게 부담이 낮은 구조");
                }
                if ("travel".equals(priority) && tags.contains("travel")) {
                    score += 22;
                    reasons.add("해외 사용 성향과 맞는 외화 혜택");
                }
                if ("often".equals(travelLevel) && tags.contains("global")) {
                    score += 28;
                    reasons.add("해외 결제 빈도가 높아 효율적");
                }
                if (request.age() <= 34 && tags.contains("young")) {
                    score += 18;
                    reasons.add("연령 우대 구간에 해당");
                }
                if (request.monthlySpend() >= 100 && tags.contains("daily")) {
                    score += 10;
                    reasons.add("생활비 지출 패턴과 적합");
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
        String priority = normalize(request.priority());
        String travelLevel = normalize(request.travelLevel());
        Set<String> categorySet = lowerSet(request.categories());

        List<CardCatalogEntity> candidates = cardCatalogRepository.findByActiveTrue().stream()
            .filter(candidate -> !lowerSet(candidate.getTags()).contains("stat-only"))
            .toList();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Card catalog is empty");
        }

        List<ScoredProduct> scored = candidates.stream()
            .map(candidate -> {
                int score = 45;
                List<String> reasons = new ArrayList<>();
                Set<String> tags = lowerSet(candidate.getTags());
                Set<String> categories = lowerSet(candidate.getCategories());

                int categoryHit = (int) categories.stream().filter(categorySet::contains).count();
                score += categoryHit * 9;
                if (categoryHit > 0) {
                    reasons.add("소비 카테고리 " + categoryHit + "개 일치");
                }

                if ("cashback".equals(priority) && tags.contains("cashback")) {
                    score += 24;
                    reasons.add("캐시백 우선순위와 적합");
                }
                if ("travel".equals(priority) && tags.contains("travel")) {
                    score += 22;
                    reasons.add("여행/해외결제 우선순위 반영");
                }
                if ("starter".equals(priority) && tags.contains("starter")) {
                    score += 24;
                    reasons.add("연회비 부담 최소화 선호와 일치");
                }
                if ("often".equals(travelLevel) && tags.contains("travel")) {
                    score += 28;
                    reasons.add("해외 결제 빈도에 유리");
                }
                if (request.monthlySpend() >= 80 && tags.contains("daily")) {
                    score += 10;
                    reasons.add("전월 실적 달성 가능성이 높음");
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
        if (cardText.contains("여행") && accountText.contains("외화")) {
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

    private Set<String> lowerSet(Iterable<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toLowerCase());
            }
        }
        return result;
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String joinReasons(List<String> reasons) {
        if (reasons.isEmpty()) {
            return "기본 조건 기반 추천";
        }
        return String.join(" · ", reasons.stream().limit(3).toList());
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
}
