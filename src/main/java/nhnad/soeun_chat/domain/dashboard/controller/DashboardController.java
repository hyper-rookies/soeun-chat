package nhnad.soeun_chat.domain.dashboard.controller;

import lombok.RequiredArgsConstructor;
import nhnad.soeun_chat.domain.dashboard.dto.DashboardSummaryResponse;
import nhnad.soeun_chat.domain.dashboard.service.DashboardService;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController implements DashboardApi {

    private final DashboardService dashboardService;
    private final CacheManager cacheManager;

    @Override
    public ApiResponse<DashboardSummaryResponse> getSummary() {
        return ApiResponse.of(dashboardService.getSummary());
    }

    @Override
    public ApiResponse<String> clearCache() {
        var cache = cacheManager.getCache("dashboard");
        if (cache != null) cache.clear();
        return ApiResponse.of("캐시 초기화 완료");
    }
}
