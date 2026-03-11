package nhnad.soeun_chat.domain.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리포트 목록 요약")
public record ReportSummary(
        @Schema(description = "리포트 대화 ID", example = "conv-report-20240311")
        String conversationId,

        @Schema(description = "리포트 제목", example = "2024년 3월 2주차 주간 리포트")
        String title,

        @Schema(description = "생성 시각 (ISO 8601 형식)", example = "2024-03-11T09:00:00")
        String createdAt,

        @Schema(description = "공유 토큰 — GET /api/share/{token} 에 사용", example = "sh_eyJhbGci...")
        String shareToken
) {}
