package nhnad.soeun_chat.domain.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import nhnad.soeun_chat.domain.report.dto.ExcelExportResponse;
import nhnad.soeun_chat.domain.report.dto.ReportRequest;
import nhnad.soeun_chat.domain.report.dto.ReportResponse;
import nhnad.soeun_chat.domain.report.dto.ReportSummary;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Report", description = "리포트 — 자동 생성 및 조회")
@RequestMapping("/api/chat")
public interface ReportApi {

    @Operation(
            summary = "리포트 자동 생성 (내부 API)",
            description = """
                    EventBridge → Lambda에서 주기적으로 호출하는 **내부 전용 API**입니다.

                    > ⚠️ **프론트엔드에서 직접 호출하지 않습니다.**
                    > 이 API는 Lambda 배치 프로세스가 호출합니다.

                    **인증:** `X-Internal-Key` 헤더에 내부 API 키를 포함해야 합니다. (Bearer 토큰 불필요)

                    **reportType 값:**
                    | 값 | 설명 |
                    |---|---|
                    | `weekly` | 주간 리포트 (매주 월요일 자동 실행) |
                    | `monthly` | 월간 리포트 (매월 1일 자동 실행) |

                    `targetUserId`가 없으면 `"system"` 으로 처리됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReportResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "shareToken": "sh_eyJhbGci...",
                                        "conversationId": "conv-report-20240311"
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "X-Internal-Key 누락 또는 불일치",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INVALID_TOKEN\",\"message\":\"유효하지 않은 내부 API 키입니다.\"}")
                    )
            )
    })
    @SecurityRequirements
    @PostMapping("/report")
    ApiResponse<ReportResponse> generateReport(
            @Parameter(in = ParameterIn.HEADER, name = "X-Internal-Key", required = true,
                    description = "내부 API 키 — Lambda 배치 프로세스용")
            @RequestHeader("X-Internal-Key") String internalKey,
            @RequestBody ReportRequest request
    );

    @Operation(
            summary = "내 리포트 목록 조회",
            description = """
                    자동 생성된 내 리포트 목록을 반환합니다.

                    리포트 목록 페이지에서 사용하세요. 각 리포트는 `shareToken`을 통해 공유 가능합니다.

                    **응답 필드:**
                    | 필드 | 설명 |
                    |---|---|
                    | `conversationId` | 리포트 대화 ID |
                    | `title` | 리포트 제목 (예: "2024년 3월 주간 리포트") |
                    | `createdAt` | 생성일시 |
                    | `shareToken` | 공유 링크 토큰 — `GET /api/share/{token}` 에 사용 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ReportSummary.class)),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "conversationId": "conv-report-20240311",
                                          "title": "2024년 3월 2주차 주간 리포트",
                                          "createdAt": "2024-03-11T09:00:00",
                                          "shareToken": "sh_eyJhbGci..."
                                        }
                                      ]
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
    @GetMapping("/reports")
    ApiResponse<List<ReportSummary>> getReports(
            @Parameter(hidden = true) @AuthenticationPrincipal String userId
    );

    @Operation(
            summary = "리포트 Excel 내보내기",
            description = """
                    리포트 대화의 데이터를 **Excel 파일로 다운로드**할 수 있는 URL을 반환합니다.

                    응답의 `downloadUrl`로 GET 요청하면 `.xlsx` 파일을 다운로드할 수 있습니다.
                    URL은 S3 Pre-signed URL로 **만료 시간이 있습니다** (보통 15분).
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Excel 내보내기 URL 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExcelExportResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "downloadUrl": "https://s3.amazonaws.com/se-report-ad-data/exports/report-20240311.xlsx?X-Amz-Expires=900&...",
                                        "fileName": "report-2024-03-11.xlsx"
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"code\":\"NOT_FOUND\",\"message\":\"리포트를 찾을 수 없습니다.\"}")
                    )
            )
    })
    @GetMapping("/report/{conversationId}/excel")
    ApiResponse<ExcelExportResponse> exportExcel(
            @Parameter(hidden = true) @AuthenticationPrincipal String userId,
            @Parameter(description = "리포트 대화 ID", example = "conv-report-20240311")
            @PathVariable String conversationId
    );
}
