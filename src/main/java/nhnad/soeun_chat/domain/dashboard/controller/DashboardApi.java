package nhnad.soeun_chat.domain.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import nhnad.soeun_chat.domain.dashboard.dto.DashboardSummaryResponse;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Dashboard", description = "대시보드 — 광고 성과 요약 지표")
@RequestMapping("/api/dashboard")
public interface DashboardApi {

    @Operation(
            summary = "대시보드 요약 조회",
            description = """
                    대시보드 메인 화면에 필요한 **광고 성과 요약 지표**를 반환합니다.
                    응답 데이터는 캐시되어 있으며, 최신 데이터가 필요한 경우 `DELETE /api/dashboard/cache` 를 먼저 호출하세요.

                    **응답 구조:**
                    | 필드 | 설명 |
                    |---|---|
                    | `adCost` | 광고비 현황 (오늘/어제/이번주, 변화율 포함) |
                    | `mediaShare` | 매체별 광고비 비중 (카카오/구글) |
                    | `dailyConversions` | 일별 전환수·클릭수 (최근 7일) |
                    | `performanceMetrics` | 핵심 성과 지표 — CPC·CTR·ROAS |

                    **변화율(`changeRate`) 계산:**
                    - `todayChangeRate`: (오늘 - 어제) / 어제 × 100 (%)
                    - `thisWeekChangeRate`: (이번주 - 지난주) / 지난주 × 100 (%)
                    - 양수 = 증가, 음수 = 감소
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DashboardSummaryResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "adCost": {
                                          "today": 450000,
                                          "yesterday": 380000,
                                          "thisWeek": 2100000,
                                          "todayChangeRate": 18.42,
                                          "thisWeekChangeRate": -5.30
                                        },
                                        "mediaShare": [
                                          { "name": "kakao", "value": 1200000 },
                                          { "name": "google", "value": 900000 }
                                        ],
                                        "dailyConversions": [
                                          { "date": "2024-03-01", "conversions": 42, "clicks": 1230 },
                                          { "date": "2024-03-02", "conversions": 38, "clicks": 1100 }
                                        ],
                                        "performanceMetrics": {
                                          "cpc": 1724,
                                          "ctr": 3.25,
                                          "roas": 410.5
                                        }
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}")
                    )
            )
    })
    @GetMapping("/summary")
    ApiResponse<DashboardSummaryResponse> getSummary();

    @Operation(
            summary = "대시보드 캐시 초기화 (개발/테스트용)",
            description = """
                    서버에 캐시된 대시보드 데이터를 삭제합니다.

                    > ⚠️ 개발·테스트 환경 전용입니다. 캐시 초기화 후 `GET /api/dashboard/summary` 를 호출하면 최신 데이터를 새로 조회합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "캐시 초기화 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":true,\"data\":\"캐시 초기화 완료\"}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}")
                    )
            )
    })
    @DeleteMapping("/cache")
    ApiResponse<String> clearCache();
}
