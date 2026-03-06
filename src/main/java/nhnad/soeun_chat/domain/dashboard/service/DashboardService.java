package nhnad.soeun_chat.domain.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.dashboard.dto.DashboardSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AthenaClient athenaClient;

    @Value("${aws.athena.database}")
    private String database;

    @Value("${aws.athena.output-location}")
    private String outputLocation;

    @Cacheable("dashboard")
    public DashboardSummaryResponse getSummary() {
        log.info("대시보드 데이터 조회 시작");

        LocalDate latestDate = getLatestDate();
        log.info("[Dashboard] 기준 최신 날짜: {}", latestDate);

        AdCostResult adCost           = queryAdCost(latestDate);
        List<MediaShareData> media    = queryMediaShare(latestDate);
        List<DailyConvData> daily     = queryDailyConversions(latestDate);
        PerformanceResult performance = queryPerformanceMetrics(latestDate);

        return new DashboardSummaryResponse(
                new DashboardSummaryResponse.AdCost(
                        adCost.today(),
                        adCost.yesterday(),
                        adCost.thisWeek(),
                        calcChangeRate(adCost.today(), adCost.yesterday()),
                        calcChangeRate(adCost.thisWeek(), adCost.lastWeek())
                ),
                media.stream().map(m -> new DashboardSummaryResponse.MediaShare(m.name(), m.value())).toList(),
                daily.stream().map(d -> new DashboardSummaryResponse.DailyConversions(d.date(), d.conversions(), d.clicks())).toList(),
                new DashboardSummaryResponse.PerformanceMetrics(
                        performance.cpc(),
                        performance.ctr(),
                        performance.roas()
                )
        );
    }

    // ── 쿼리 1: 광고비 (오늘/어제/이번주) ──────────────────────────────
    private record AdCostResult(long today, long yesterday, long thisWeek, long lastWeek) {}

    private AdCostResult queryAdCost(LocalDate latestDate) {
        String year  = String.valueOf(latestDate.getYear());
        String month = String.format("%02d", latestDate.getMonthValue());

        // cost_micros 컬럼명과 달리 실제 저장값은 원(KRW) 단위
        String sql =
            "SELECT " +
            "  SUBSTR(CAST(basic_date AS VARCHAR), 1, 4) || '-' || " +
            "  SUBSTR(CAST(basic_date AS VARCHAR), 5, 2) || '-' || " +
            "  SUBSTR(CAST(basic_date AS VARCHAR), 7, 2) AS date_str, " +
            "  SUM(CAST(cost_micros AS DOUBLE)) AS cost " +
            "FROM se_report_db.google_ad_performance " +
            "WHERE year='" + year + "' AND month_p='" + month + "' " +
            "GROUP BY basic_date " +
            "UNION ALL " +
            "SELECT " +
            "  SUBSTR(CAST(basic_date AS VARCHAR), 1, 4) || '-' || " +
            "  SUBSTR(CAST(basic_date AS VARCHAR), 5, 2) || '-' || " +
            "  SUBSTR(CAST(basic_date AS VARCHAR), 7, 2) AS date_str, " +
            "  CAST(SUM(spending) AS DOUBLE) AS cost " +
            "FROM se_report_db.kakao_ad_performance " +
            "WHERE year='" + year + "' AND month_p='" + month + "' " +
            "GROUP BY basic_date";

        List<Row> rows = executeAndGetRows(sql);

        // "오늘"은 LocalDate.now()가 아닌 latestDate(DB 최신 날짜) 기준
        // 전주 데이터가 동일 month_p에 없으면 lastWeek=0 허용
        LocalDate weekStart     = latestDate.with(DayOfWeek.MONDAY);
        LocalDate lastWeekStart = weekStart.minusWeeks(1);
        String latestStr        = latestDate.toString();
        String prevDayStr       = latestDate.minusDays(1).toString();

        long todayCost = 0, yesterdayCost = 0, thisWeek = 0, lastWeek = 0;

        for (Row row : rows) {
            String dateStr    = row.data().get(0).varCharValue();
            long cost         = parseLong(row.data().get(1).varCharValue());
            LocalDate rowDate = LocalDate.parse(dateStr);

            if (dateStr.equals(latestStr))                                         todayCost     += cost;
            if (dateStr.equals(prevDayStr))                                        yesterdayCost += cost;
            if (!rowDate.isBefore(weekStart))                                      thisWeek      += cost;
            if (!rowDate.isBefore(lastWeekStart) && rowDate.isBefore(weekStart))   lastWeek      += cost;
        }

        return new AdCostResult(todayCost, yesterdayCost, thisWeek, lastWeek);
    }

    // ── 쿼리 2: 매체별 비중 ──────────────────────────────────────────
    private record MediaShareData(String name, long value) {}

    private List<MediaShareData> queryMediaShare(LocalDate latestDate) {
        String year  = String.valueOf(latestDate.getYear());
        String month = String.format("%02d", latestDate.getMonthValue());

        // cost_micros 컬럼명과 달리 실제 저장값은 원(KRW) 단위
        String sql =
            "SELECT '구글' AS media, CAST(ROUND(SUM(CAST(cost_micros AS DOUBLE)), 0) AS BIGINT) AS cost " +
            "FROM se_report_db.google_ad_performance " +
            "WHERE year='" + year + "' AND month_p='" + month + "' " +
            "UNION ALL " +
            "SELECT '카카오' AS media, CAST(SUM(spending) AS BIGINT) AS cost " +
            "FROM se_report_db.kakao_ad_performance " +
            "WHERE year='" + year + "' AND month_p='" + month + "'";

        List<Row> rows = executeAndGetRows(sql);
        List<MediaShareData> result = new ArrayList<>();
        for (Row row : rows) {
            String name = row.data().get(0).varCharValue();
            long cost   = parseLong(row.data().get(1).varCharValue());
            result.add(new MediaShareData(name, cost));
        }
        return result;
    }

    // ── 쿼리 3: 최근 7일 일별 전환 추이 ─────────────────────────────
    private record DailyConvData(String date, long conversions, long clicks) {}

    private List<DailyConvData> queryDailyConversions(LocalDate latestDate) {
        String year      = String.valueOf(latestDate.getYear());
        String month     = String.format("%02d", latestDate.getMonthValue());
        String dateFrom  = latestDate.minusDays(6).toString();
        String dateTo    = latestDate.toString();

        String sql =
            "SELECT date_str, SUM(conv) AS total_conv, SUM(clicks) AS total_clicks " +
            "FROM ( " +
            "  SELECT " +
            "    SUBSTR(CAST(basic_date AS VARCHAR), 1, 4) || '-' || " +
            "    SUBSTR(CAST(basic_date AS VARCHAR), 5, 2) || '-' || " +
            "    SUBSTR(CAST(basic_date AS VARCHAR), 7, 2) AS date_str, " +
            "    CAST(conversions AS BIGINT) AS conv, " +  // 구글 conversions: 스키마 double → BIGINT CAST 필수
            "    clicks AS clicks " +
            "  FROM se_report_db.google_ad_performance " +
            "  WHERE year='" + year + "' AND month_p='" + month + "' " +
            "  UNION ALL " +
            "  SELECT " +
            "    SUBSTR(CAST(basic_date AS VARCHAR), 1, 4) || '-' || " +
            "    SUBSTR(CAST(basic_date AS VARCHAR), 5, 2) || '-' || " +
            "    SUBSTR(CAST(basic_date AS VARCHAR), 7, 2) AS date_str, " +
            "    CAST(conv_purchase_1d AS BIGINT) AS conv, " +  // 카카오 conv_purchase_1d: Parquet INT64 vs 스키마 double 불일치 → CAST 필수
            "    click AS clicks " +
            "  FROM se_report_db.kakao_ad_performance " +
            "  WHERE year='" + year + "' AND month_p='" + month + "' " +
            ") " +
            "WHERE date_str >= '" + dateFrom + "' AND date_str <= '" + dateTo + "' " +
            "GROUP BY date_str " +
            "ORDER BY date_str ASC";

        List<Row> rows = executeAndGetRows(sql);
        List<DailyConvData> result = new ArrayList<>();
        for (Row row : rows) {
            String date = row.data().get(0).varCharValue();
            long conv   = parseLong(row.data().get(1).varCharValue());
            long clicks = parseLong(row.data().get(2).varCharValue());
            result.add(new DailyConvData(date, conv, clicks));
        }
        return result;
    }

    // ── 쿼리 4: 종합 성과 지표 ────────────────────────────────────────
    private record PerformanceResult(long cpc, double ctr, double roas) {}

    private PerformanceResult queryPerformanceMetrics(LocalDate latestDate) {
        String year  = String.valueOf(latestDate.getYear());
        String month = String.format("%02d", latestDate.getMonthValue());

        // cost_micros 컬럼명과 달리 실제 저장값은 원(KRW) 단위
        String sql =
            "SELECT SUM(cost) AS total_cost, SUM(clicks) AS total_clicks, " +
            "       SUM(impressions) AS total_impressions, SUM(conv_value) AS total_conv_value " +
            "FROM ( " +
            "  SELECT " +
            "    SUM(CAST(cost_micros AS DOUBLE)) AS cost, " +
            "    SUM(clicks)                      AS clicks, " +
            "    SUM(impressions)                 AS impressions, " +
            "    SUM(conversions_value)           AS conv_value " +
            "  FROM se_report_db.google_ad_performance " +
            "  WHERE year='" + year + "' AND month_p='" + month + "' " +
            "  UNION ALL " +
            // TODO: 카카오 ctr, ppc 컬럼 활용한 가중평균 개선 가능
            "  SELECT " +
            "    CAST(SUM(spending) AS DOUBLE) AS cost, " +
            "    SUM(click)                    AS clicks, " +
            "    SUM(imp)                      AS impressions, " +
            // 카카오 전환가치 컬럼 없음 → ROAS는 구글 기준으로만 계산
            // TODO: 카카오 ROAS 계산 방식 별도 논의 필요
            "    0.0                           AS conv_value " +
            "  FROM se_report_db.kakao_ad_performance " +
            "  WHERE year='" + year + "' AND month_p='" + month + "' " +
            ")";

        List<Row> rows = executeAndGetRows(sql);
        if (rows.isEmpty()) return new PerformanceResult(0, 0.0, 0.0);

        Row row = rows.get(0);
        long   totalCost        = parseLong(row.data().get(0).varCharValue());
        long   totalClicks      = parseLong(row.data().get(1).varCharValue());
        long   totalImpressions = parseLong(row.data().get(2).varCharValue());
        double totalConvValue   = parseDouble(row.data().get(3).varCharValue());

        long   cpc  = totalClicks > 0 ? totalCost / totalClicks : 0;
        double ctr  = totalImpressions > 0
                      ? Math.round((double) totalClicks / totalImpressions * 1000.0) / 10.0
                      : 0.0;
        double roas = totalCost > 0
                      ? Math.round(totalConvValue / totalCost * 1000.0) / 10.0
                      : 0.0;

        return new PerformanceResult(cpc, ctr, roas);
    }

    // ── 날짜 헬퍼 ────────────────────────────────────────────────
    // DB에 적재된 가장 최신 basic_date를 기준 날짜로 사용 (현재 날짜와 불일치 방지)
    private LocalDate getLatestDate() {
        String sql = """
            SELECT MAX(basic_date) AS latest
            FROM (
              SELECT MAX(basic_date) AS basic_date
              FROM se_report_db.google_ad_performance
              UNION ALL
              SELECT MAX(basic_date) AS basic_date
              FROM se_report_db.kakao_ad_performance
            )
            """;

        List<Row> rows = executeAndGetRows(sql);
        if (rows.isEmpty()) return LocalDate.now();

        String raw = rows.get(0).data().get(0).varCharValue();
        // basic_date가 20260207 형태(bigint)이므로 파싱
        String formatted = raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8);
        return LocalDate.parse(formatted);
    }

    // ── 공통 유틸 ─────────────────────────────────────────────────
    private List<Row> executeAndGetRows(String sql) {
        try {
            log.info("[Dashboard] 쿼리 실행: {}", sql.substring(0, Math.min(sql.length(), 100)));

            String queryId = athenaClient.startQueryExecution(
                    StartQueryExecutionRequest.builder()
                            .queryString(sql)
                            .queryExecutionContext(QueryExecutionContext.builder()
                                    .database(database).build())
                            .resultConfiguration(ResultConfiguration.builder()
                                    .outputLocation(outputLocation).build())
                            .build()
            ).queryExecutionId();

            while (true) {
                QueryExecutionStatus status = athenaClient.getQueryExecution(
                        GetQueryExecutionRequest.builder().queryExecutionId(queryId).build()
                ).queryExecution().status();

                QueryExecutionState state = status.state();
                if (state == QueryExecutionState.SUCCEEDED) break;
                if (state == QueryExecutionState.FAILED || state == QueryExecutionState.CANCELLED) {
                    log.error("[Dashboard] 쿼리 실패: {}", status.stateChangeReason());
                    return List.of();
                }
                Thread.sleep(500);
            }

            List<Row> rows = athenaClient.getQueryResults(
                    GetQueryResultsRequest.builder().queryExecutionId(queryId).build()
            ).resultSet().rows();

            log.info("[Dashboard] 쿼리 결과: {}행 (헤더 포함)", rows.size());
            return rows.size() > 1 ? rows.subList(1, rows.size()) : List.of();

        } catch (Exception e) {
            log.error("[Dashboard] 쿼리 실행 오류: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private double calcChangeRate(long current, long previous) {
        if (previous == 0) return 0.0;
        double rate = (double)(current - previous) / previous * 100.0;
        return Math.round(rate * 10.0) / 10.0;
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return (long) Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
