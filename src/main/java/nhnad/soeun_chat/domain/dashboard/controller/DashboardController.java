package nhnad.soeun_chat.domain.dashboard.controller;

import lombok.RequiredArgsConstructor;
import nhnad.soeun_chat.domain.dashboard.dto.DashboardSummaryResponse;
import nhnad.soeun_chat.domain.dashboard.service.DashboardService;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CacheManager cacheManager;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    // 개발/테스트용 캐시 초기화
    @DeleteMapping("/cache")
    public ResponseEntity<String> clearCache() {
        var cache = cacheManager.getCache("dashboard");
        if (cache != null) cache.clear();
        return ResponseEntity.ok("캐시 초기화 완료");
    }
}
