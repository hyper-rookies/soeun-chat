package nhnad.soeun_chat.domain.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "대시보드 요약 응답")
public record DashboardSummaryResponse(
        @Schema(description = "광고비 현황")
        AdCost adCost,

        @Schema(description = "매체별 광고비 비중 (카카오/구글)")
        List<MediaShare> mediaShare,

        @Schema(description = "일별 전환수·클릭수 (최근 7일, 날짜 오름차순)")
        List<DailyConversions> dailyConversions,

        @Schema(description = "핵심 성과 지표 (KPI)")
        PerformanceMetrics performanceMetrics
) {
    @Schema(description = "광고비 현황")
    public record AdCost(
            @Schema(description = "오늘 광고비 (원)", example = "450000")
            long today,

            @Schema(description = "어제 광고비 (원)", example = "380000")
            long yesterday,

            @Schema(description = "이번 주 누적 광고비 (원)", example = "2100000")
            long thisWeek,

            @Schema(description = "전일 대비 광고비 변화율 (%). 양수=증가, 음수=감소", example = "18.42")
            double todayChangeRate,

            @Schema(description = "전주 대비 광고비 변화율 (%). 양수=증가, 음수=감소", example = "-5.30")
            double thisWeekChangeRate
    ) {}

    @Schema(description = "매체별 광고비")
    public record MediaShare(
            @Schema(description = "매체명", example = "kakao", allowableValues = {"kakao", "google"})
            String name,

            @Schema(description = "광고비 (원)", example = "1200000")
            long value
    ) {}

    @Schema(description = "일별 전환·클릭 데이터")
    public record DailyConversions(
            @Schema(description = "날짜 (yyyy-MM-dd)", example = "2024-03-01")
            String date,

            @Schema(description = "전환수", example = "42")
            long conversions,

            @Schema(description = "클릭수", example = "1230")
            long clicks
    ) {}

    @Schema(description = "핵심 성과 지표 (KPI)")
    public record PerformanceMetrics(
            @Schema(description = "CPC — 클릭당 비용 (원)", example = "1724")
            long cpc,

            @Schema(description = "CTR — 클릭률 (%)", example = "3.25")
            double ctr,

            @Schema(description = "ROAS — 광고비 대비 매출 비율 (%)", example = "410.5")
            double roas
    ) {}
}
