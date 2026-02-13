package com.benepick.recommendation.service;

import com.benepick.recommendation.entity.AccountCatalogEntity;
import com.benepick.recommendation.entity.CardCatalogEntity;
import com.benepick.recommendation.repository.AccountCatalogRepository;
import com.benepick.recommendation.repository.CardCatalogRepository;
import java.util.List;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class RecommendationCatalogInitializer implements CommandLineRunner {

    private final AccountCatalogRepository accountCatalogRepository;
    private final CardCatalogRepository cardCatalogRepository;

    public RecommendationCatalogInitializer(
        AccountCatalogRepository accountCatalogRepository,
        CardCatalogRepository cardCatalogRepository
    ) {
        this.accountCatalogRepository = accountCatalogRepository;
        this.cardCatalogRepository = cardCatalogRepository;
    }

    @Override
    public void run(String... args) {
        seedAccountsIfEmpty();
        seedCardsIfEmpty();
    }

    private void seedAccountsIfEmpty() {
        if (accountCatalogRepository.count() > 0) {
            return;
        }

        List<AccountCatalogEntity> rows = List.of(
            new AccountCatalogEntity(
                "acc_kb_salary",
                "KB국민은행",
                "급여우대 플러스 통장",
                "입출금",
                "급여이체 + 생활비 자동이체 조건에서 수수료/우대 혜택",
                "https://obank.kbstar.com",
                true,
                Set.of("salary", "daily", "cashback")
            ),
            new AccountCatalogEntity(
                "acc_sh_save",
                "신한은행",
                "목표저축 챌린지 적금",
                "저축",
                "저축 목표 달성형 우대금리 제공",
                "https://www.shinhan.com",
                true,
                Set.of("savings", "goal", "auto")
            ),
            new AccountCatalogEntity(
                "acc_kakao_start",
                "카카오뱅크",
                "스타트업 프렌들리 통장",
                "입출금",
                "초기 금융 사용자를 위한 수수료 부담 완화",
                "https://www.kakaobank.com",
                true,
                Set.of("starter", "young", "low-fee")
            ),
            new AccountCatalogEntity(
                "acc_woori_fx",
                "우리은행",
                "글로벌 트래블 외화통장",
                "외화",
                "환전 우대와 해외 결제 사용자를 위한 외화 혜택",
                "https://www.wooribank.com",
                true,
                Set.of("travel", "global", "fx")
            )
        );

        accountCatalogRepository.saveAll(rows);
    }

    private void seedCardsIfEmpty() {
        if (cardCatalogRepository.count() > 0) {
            return;
        }

        List<CardCatalogEntity> rows = List.of(
            new CardCatalogEntity(
                "card_shopping_plus",
                "신한카드",
                "생활혜택 플러스",
                "국내전용 1.2만원",
                "장보기/교통/외식 중심 캐시백",
                "https://www.shinhancard.com",
                true,
                Set.of("cashback", "daily"),
                Set.of("grocery", "transport", "dining")
            ),
            new CardCatalogEntity(
                "card_kb_online",
                "KB국민카드",
                "온라인 맥스",
                "국내전용 1.0만원",
                "온라인쇼핑/구독/간편결제 특화",
                "https://card.kbcard.com",
                true,
                Set.of("cashback", "online"),
                Set.of("online", "subscription", "cafe")
            ),
            new CardCatalogEntity(
                "card_samsung_travel",
                "삼성카드",
                "트래블 마일",
                "국내외겸용 2.5만원",
                "해외결제 적립 + 여행 보너스",
                "https://www.samsungcard.com",
                true,
                Set.of("travel", "mileage"),
                Set.of("online")
            ),
            new CardCatalogEntity(
                "card_nh_starter",
                "NH농협카드",
                "스타트 제로",
                "국내전용 없음",
                "연회비 부담 최소 + 기본 적립",
                "https://card.nonghyup.com",
                true,
                Set.of("starter", "no-fee"),
                Set.of("online", "grocery", "transport")
            )
        );

        cardCatalogRepository.saveAll(rows);
    }
}
