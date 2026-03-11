package nhnad.soeun_chat.domain.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리포트 자동 생성 요청 (Lambda 배치 전용)")
public record ReportRequest(
        @Schema(description = "리포트 종류 — weekly(주간) 또는 monthly(월간)",
                example = "weekly", allowableValues = {"weekly", "monthly"})
        String reportType,

        @Schema(description = "리포트를 생성할 대상 유저 ID (null이면 system으로 처리)", example = "550e8400-e29b-41d4-a716-446655440000", nullable = true)
        String targetUserId
) {}
