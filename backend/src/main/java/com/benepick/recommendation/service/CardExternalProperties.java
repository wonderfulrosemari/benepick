package com.benepick.recommendation.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "catalog.card-external")
public class CardExternalProperties {

    /**
     * source: 기존 JSON 파일/URL 동기화
     * public-data: 공공데이터 API 단일 소스 호출
     * public-data-all: 공공데이터 카드 관련 다중 소스 순차 호출
     */
    private String mode = "source";

    private String sourceUrl = "";

    private int connectTimeoutMs = 4000;

    private int readTimeoutMs = 10000;

    private PublicData publicData = new PublicData();

    private PublicDataAll publicDataAll = new PublicDataAll();

    @Getter
    @Setter
    public static class PublicData {

        /**
         * 공공데이터 API 전체 URL
         * 예) https://apis.data.go.kr/.../getList
         */
        private String url = "";

        private String serviceKey = "";

        private String serviceKeyParam = "serviceKey";

        private String pageNo = "1";

        private String numOfRows = "200";

        /**
         * 추가 쿼리스트링 (k=v&k2=v2 형태)
         */
        private String extraQuery = "";

        /**
         * 응답에서 배열 경로를 직접 지정하고 싶을 때 사용
         * 예) response.body.items.item
         */
        private String itemsPath = "";

        /**
         * 가능한 API에서 JSON 응답 강제를 위해 _type=json, resultType=json 자동 추가
         */
        private boolean forceJson = true;

        /**
         * 데이터에 제공기관 필드가 없을 때 기본 제공기관명
         */
        private String defaultProviderName = "";

        private String officialUrlFallback = "";

        /**
         * 쉼표 구분 태그
         */
        private String defaultTags = "external";

        /**
         * 쉼표 구분 카테고리
         */
        private String defaultCategories = "";
    }

    @Getter
    @Setter
    public static class PublicDataAll {

        private String serviceKey = "";

        private boolean includeKdb = true;

        private boolean includeKrpost = true;

        private boolean includeFinanceStats = true;

        private Kdb kdb = new Kdb();

        private Krpost krpost = new Krpost();

        private FinanceStats financeStats = new FinanceStats();
    }

    @Getter
    @Setter
    public static class Kdb {

        private String url = "https://apis.data.go.kr/B190030/GetCardProductInfoService/getCardProductList";

        private String pageNo = "1";

        private String numOfRows = "500";

        private String startDate = "20210101";

        private String endDate = "20991231";

        private boolean forceJson = true;

        private String itemsPath = "response.body.items.item";

        private String defaultProviderName = "한국산업은행";

        private String officialUrlFallback = "https://www.kdb.co.kr";
    }

    @Getter
    @Setter
    public static class Krpost {

        private String url = "https://opap.ipostbank.co.kr/data/CheckcardGoods";

        private String pageNo = "1";

        private String numOfRows = "200";

        private String productNameKeyword = "브라보";

        private String itemsPath = "response.body.items.item";

        private String defaultProviderName = "우체국";

        private String officialUrlFallback = "https://www.epostbank.go.kr";
    }

    @Getter
    @Setter
    public static class FinanceStats {

        private String url = "https://apis.data.go.kr/1160100/service/GetCredCardCompInfoService/getCredCardCompGeneInfo";

        private String pageNo = "1";

        private String numOfRows = "500";

        private String title = "신용카드사 일반현황";

        /**
         * 비워두면 코드에서 전달 시점의 전달월(yyyyMM) 자동 사용
         */
        private String baseYearMonth = "";

        private String resultType = "json";

        private String itemsPath = "response.body.items.item";

        private String defaultProviderName = "신용카드사";

        private String officialUrlFallback = "https://www.fsc.go.kr";
    }
}
