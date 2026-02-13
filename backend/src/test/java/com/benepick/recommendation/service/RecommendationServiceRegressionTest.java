package com.benepick.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.benepick.recommendation.dto.RecommendationRunResponse;
import com.benepick.recommendation.dto.SimulateRecommendationRequest;
import com.benepick.recommendation.entity.AccountCatalogEntity;
import com.benepick.recommendation.entity.CardCatalogEntity;
import com.benepick.recommendation.entity.RecommendationRunEntity;
import com.benepick.recommendation.repository.AccountCatalogRepository;
import com.benepick.recommendation.repository.CardCatalogRepository;
import com.benepick.recommendation.repository.RecommendationItemRepository;
import com.benepick.recommendation.repository.RecommendationRedirectEventRepository;
import com.benepick.recommendation.repository.RecommendationRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceRegressionTest {

    @Mock
    private RecommendationRunRepository recommendationRunRepository;

    @Mock
    private RecommendationItemRepository recommendationItemRepository;

    @Mock
    private RecommendationRedirectEventRepository recommendationRedirectEventRepository;

    @Mock
    private AccountCatalogRepository accountCatalogRepository;

    @Mock
    private CardCatalogRepository cardCatalogRepository;

    private RecommendationScoringProperties scoringProperties;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        scoringProperties = new RecommendationScoringProperties();
        recommendationService = new RecommendationService(
            recommendationRunRepository,
            recommendationItemRepository,
            recommendationRedirectEventRepository,
            accountCatalogRepository,
            cardCatalogRepository,
            scoringProperties
        );

        when(recommendationRunRepository.save(any(RecommendationRunEntity.class))).thenAnswer(invocation -> {
            RecommendationRunEntity run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(run, "createdAt", OffsetDateTime.now());
            return run;
        });

        when(recommendationItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void simulate_should_rank_expected_top_products_for_fixed_input() {
        when(accountCatalogRepository.findByActiveTrue()).thenReturn(sampleAccounts());
        when(cardCatalogRepository.findByActiveTrue()).thenReturn(sampleCards());

        RecommendationRunResponse response = recommendationService.simulate(sampleRequest());

        assertThat(response.accounts()).isNotEmpty();
        assertThat(response.cards()).isNotEmpty();

        assertThat(response.accounts().get(0).productId()).isEqualTo("acc_salary_saving");
        assertThat(response.cards().get(0).productId()).isEqualTo("card_daily_core");

        assertThat(response.accounts().get(0).reason())
            .contains("최고 금리")
            .contains("기본 금리")
            .contains("급여이체");

        assertThat(response.cards().get(0).reason())
            .contains("소비 카테고리 일치")
            .contains("연회비 부담이 낮음");
    }

    @Test
    void simulate_should_score_aggressive_profile_higher_than_conservative() {
        when(accountCatalogRepository.findByActiveTrue()).thenReturn(sampleAccounts());
        when(cardCatalogRepository.findByActiveTrue()).thenReturn(sampleCards());

        scoringProperties.setProfile("conservative");
        RecommendationRunResponse conservative = recommendationService.simulate(sampleRequest());

        scoringProperties.setProfile("aggressive");
        RecommendationRunResponse aggressive = recommendationService.simulate(sampleRequest());

        assertThat(aggressive.expectedNetMonthlyProfit())
            .isGreaterThan(conservative.expectedNetMonthlyProfit());
    }

    private SimulateRecommendationRequest sampleRequest() {
        return new SimulateRecommendationRequest(
            29,
            420,
            140,
            "savings",
            "yes",
            "sometimes",
            List.of("grocery", "transport", "online")
        );
    }

    private List<AccountCatalogEntity> sampleAccounts() {
        AccountCatalogEntity top = new AccountCatalogEntity(
            "acc_salary_saving",
            "테스트은행A",
            "급여우대 적금",
            "적금",
            "최고 4.10% (기본 2.60%) · 급여이체 우대 금리 제공",
            "https://example.com/accounts/1",
            true,
            Set.of("salary", "savings")
        );

        AccountCatalogEntity mid = new AccountCatalogEntity(
            "acc_only_saving",
            "테스트은행B",
            "기본 적금",
            "적금",
            "최고 3.10% (기본 2.90%) · 일반 우대조건",
            "https://example.com/accounts/2",
            true,
            Set.of("savings")
        );

        AccountCatalogEntity low = new AccountCatalogEntity(
            "acc_starter",
            "테스트은행C",
            "스타터 통장",
            "입출금",
            "수수료 면제 중심",
            "https://example.com/accounts/3",
            true,
            Set.of("starter")
        );

        return List.of(top, mid, low);
    }

    private List<CardCatalogEntity> sampleCards() {
        CardCatalogEntity top = new CardCatalogEntity(
            "card_daily_core",
            "테스트카드A",
            "생활 코어",
            "국내전용 없음",
            "온라인/교통/장보기 중심 할인",
            "https://example.com/cards/1",
            true,
            Set.of("daily"),
            Set.of("online", "transport", "grocery")
        );

        CardCatalogEntity mid = new CardCatalogEntity(
            "card_online",
            "테스트카드B",
            "온라인 플러스",
            "국내전용 1.5만원",
            "온라인/구독 특화",
            "https://example.com/cards/2",
            true,
            Set.of("online"),
            Set.of("online", "subscription")
        );

        CardCatalogEntity filtered = new CardCatalogEntity(
            "card_stat_only",
            "테스트카드C",
            "통계용 카드",
            "국내전용 5천원",
            "통계 집계 전용",
            "https://example.com/cards/3",
            true,
            Set.of("stat-only"),
            Set.of("online")
        );

        return List.of(top, mid, filtered);
    }
}
