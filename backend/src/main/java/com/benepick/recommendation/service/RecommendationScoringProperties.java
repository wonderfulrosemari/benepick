package com.benepick.recommendation.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "recommendation.scoring")
public class RecommendationScoringProperties {

    private String profile = "balanced";

    private Account account = new Account();

    private Card card = new Card();

    public Account resolvedAccount() {
        Account resolved = account.copy();
        switch (normalizeProfile(profile)) {
            case "conservative" -> {
                resolved.setSalaryTransferWeight(scale(resolved.getSalaryTransferWeight(), 0.90));
                resolved.setTravelOftenGlobalWeight(scale(resolved.getTravelOftenGlobalWeight(), 0.85));
                resolved.setYoungWeight(scale(resolved.getYoungWeight(), 0.90));
                resolved.setDailySpendWeight(scale(resolved.getDailySpendWeight(), 0.85));
                resolved.setIntentCategoryHitWeight(scale(resolved.getIntentCategoryHitWeight(), 0.80));
                resolved.setPrioritySavingsWeight(scale(resolved.getPrioritySavingsWeight(), 0.85));
                resolved.setPriorityStarterWeight(scale(resolved.getPriorityStarterWeight(), 0.85));
                resolved.setPriorityTravelWeight(scale(resolved.getPriorityTravelWeight(), 0.85));
                resolved.setPriorityCashbackWeight(scale(resolved.getPriorityCashbackWeight(), 0.85));
                resolved.setPrioritySalaryWeight(scale(resolved.getPrioritySalaryWeight(), 0.85));
                resolved.setHighRateBonusWeight(scale(resolved.getHighRateBonusWeight(), 0.85));
            }
            case "aggressive" -> {
                resolved.setSalaryTransferWeight(scale(resolved.getSalaryTransferWeight(), 1.15));
                resolved.setTravelOftenGlobalWeight(scale(resolved.getTravelOftenGlobalWeight(), 1.20));
                resolved.setYoungWeight(scale(resolved.getYoungWeight(), 1.10));
                resolved.setDailySpendWeight(scale(resolved.getDailySpendWeight(), 1.15));
                resolved.setIntentCategoryHitWeight(scale(resolved.getIntentCategoryHitWeight(), 1.20));
                resolved.setPrioritySavingsWeight(scale(resolved.getPrioritySavingsWeight(), 1.20));
                resolved.setPriorityStarterWeight(scale(resolved.getPriorityStarterWeight(), 1.20));
                resolved.setPriorityTravelWeight(scale(resolved.getPriorityTravelWeight(), 1.20));
                resolved.setPriorityCashbackWeight(scale(resolved.getPriorityCashbackWeight(), 1.20));
                resolved.setPrioritySalaryWeight(scale(resolved.getPrioritySalaryWeight(), 1.20));
                resolved.setHighRateBonusWeight(scale(resolved.getHighRateBonusWeight(), 1.20));
            }
            default -> {
            }
        }
        return resolved;
    }

    public Card resolvedCard() {
        Card resolved = card.copy();
        switch (normalizeProfile(profile)) {
            case "conservative" -> {
                resolved.setCategoryHitWeight(scale(resolved.getCategoryHitWeight(), 0.85));
                resolved.setPriorityCashbackWeight(scale(resolved.getPriorityCashbackWeight(), 0.85));
                resolved.setPriorityTravelWeight(scale(resolved.getPriorityTravelWeight(), 0.85));
                resolved.setPriorityStarterWeight(scale(resolved.getPriorityStarterWeight(), 0.85));
                resolved.setPrioritySavingsWeight(scale(resolved.getPrioritySavingsWeight(), 0.85));
                resolved.setPriorityAnnualFeeWeight(scale(resolved.getPriorityAnnualFeeWeight(), 0.85));
                resolved.setTravelOftenWeight(scale(resolved.getTravelOftenWeight(), 0.85));
                resolved.setDailySpendWeight(scale(resolved.getDailySpendWeight(), 0.85));
                resolved.setLowAnnualFeeBonusWeight(scale(resolved.getLowAnnualFeeBonusWeight(), 0.90));
                resolved.setHighAnnualFeePenaltyWeight(scale(resolved.getHighAnnualFeePenaltyWeight(), 1.20));
                resolved.setHighAnnualFeeThresholdWon(scale(resolved.getHighAnnualFeeThresholdWon(), 0.90));
            }
            case "aggressive" -> {
                resolved.setCategoryHitWeight(scale(resolved.getCategoryHitWeight(), 1.20));
                resolved.setPriorityCashbackWeight(scale(resolved.getPriorityCashbackWeight(), 1.20));
                resolved.setPriorityTravelWeight(scale(resolved.getPriorityTravelWeight(), 1.20));
                resolved.setPriorityStarterWeight(scale(resolved.getPriorityStarterWeight(), 1.20));
                resolved.setPrioritySavingsWeight(scale(resolved.getPrioritySavingsWeight(), 1.20));
                resolved.setPriorityAnnualFeeWeight(scale(resolved.getPriorityAnnualFeeWeight(), 1.20));
                resolved.setTravelOftenWeight(scale(resolved.getTravelOftenWeight(), 1.20));
                resolved.setDailySpendWeight(scale(resolved.getDailySpendWeight(), 1.20));
                resolved.setLowAnnualFeeBonusWeight(scale(resolved.getLowAnnualFeeBonusWeight(), 1.15));
                resolved.setHighAnnualFeePenaltyWeight(scale(resolved.getHighAnnualFeePenaltyWeight(), 0.80));
                resolved.setHighAnnualFeeThresholdWon(scale(resolved.getHighAnnualFeeThresholdWon(), 1.20));
            }
            default -> {
            }
        }
        return resolved;
    }

    private static String normalizeProfile(String value) {
        return value == null ? "balanced" : value.trim().toLowerCase();
    }

    private static int scale(int value, double factor) {
        return Math.max(1, (int) Math.round(value * factor));
    }

    @Getter
    @Setter
    public static class Account {

        private int baseScore = 45;

        private int salaryTransferWeight = 30;

        private int travelOftenGlobalWeight = 28;

        private int youngWeight = 18;

        private int dailySpendWeight = 10;

        private int intentCategoryHitWeight = 6;

        private int prioritySavingsWeight = 34;

        private int priorityStarterWeight = 24;

        private int priorityTravelWeight = 22;

        private int priorityCashbackWeight = 14;

        private int prioritySalaryWeight = 30;

        private int highRateBonusWeight = 8;

        private int youngAgeMax = 34;

        private int dailySpendThreshold = 100;

        private double highRateThreshold = 3.5;

        private Account copy() {
            Account copy = new Account();
            copy.baseScore = this.baseScore;
            copy.salaryTransferWeight = this.salaryTransferWeight;
            copy.travelOftenGlobalWeight = this.travelOftenGlobalWeight;
            copy.youngWeight = this.youngWeight;
            copy.dailySpendWeight = this.dailySpendWeight;
            copy.intentCategoryHitWeight = this.intentCategoryHitWeight;
            copy.prioritySavingsWeight = this.prioritySavingsWeight;
            copy.priorityStarterWeight = this.priorityStarterWeight;
            copy.priorityTravelWeight = this.priorityTravelWeight;
            copy.priorityCashbackWeight = this.priorityCashbackWeight;
            copy.prioritySalaryWeight = this.prioritySalaryWeight;
            copy.highRateBonusWeight = this.highRateBonusWeight;
            copy.youngAgeMax = this.youngAgeMax;
            copy.dailySpendThreshold = this.dailySpendThreshold;
            copy.highRateThreshold = this.highRateThreshold;
            return copy;
        }
    }

    @Getter
    @Setter
    public static class Card {

        private int baseScore = 45;

        private int categoryHitWeight = 9;

        private int priorityCashbackWeight = 24;

        private int priorityTravelWeight = 22;

        private int priorityStarterWeight = 24;

        private int prioritySavingsWeight = 14;

        private int priorityAnnualFeeWeight = 26;

        private int travelOftenWeight = 28;

        private int dailySpendWeight = 10;

        private int lowAnnualFeeBonusWeight = 8;

        private int highAnnualFeePenaltyWeight = 6;

        private int dailySpendThreshold = 80;

        private int highAnnualFeeThresholdWon = 20000;

        private Card copy() {
            Card copy = new Card();
            copy.baseScore = this.baseScore;
            copy.categoryHitWeight = this.categoryHitWeight;
            copy.priorityCashbackWeight = this.priorityCashbackWeight;
            copy.priorityTravelWeight = this.priorityTravelWeight;
            copy.priorityStarterWeight = this.priorityStarterWeight;
            copy.prioritySavingsWeight = this.prioritySavingsWeight;
            copy.priorityAnnualFeeWeight = this.priorityAnnualFeeWeight;
            copy.travelOftenWeight = this.travelOftenWeight;
            copy.dailySpendWeight = this.dailySpendWeight;
            copy.lowAnnualFeeBonusWeight = this.lowAnnualFeeBonusWeight;
            copy.highAnnualFeePenaltyWeight = this.highAnnualFeePenaltyWeight;
            copy.dailySpendThreshold = this.dailySpendThreshold;
            copy.highAnnualFeeThresholdWon = this.highAnnualFeeThresholdWon;
            return copy;
        }
    }
}
