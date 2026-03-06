package nhnad.soeun_chat.domain.dashboard.dto;

import java.util.List;

public record DashboardSummaryResponse(
        AdCost adCost,
        List<MediaShare> mediaShare,
        List<DailyConversions> dailyConversions,
        PerformanceMetrics performanceMetrics
) {
    public record AdCost(
            long today,
            long yesterday,
            long thisWeek,
            double todayChangeRate,
            double thisWeekChangeRate
    ) {}

    public record MediaShare(
            String name,
            long value
    ) {}

    public record DailyConversions(
            String date,
            long conversions,
            long clicks
    ) {}

    public record PerformanceMetrics(
            long cpc,
            double ctr,
            double roas
    ) {}
}
