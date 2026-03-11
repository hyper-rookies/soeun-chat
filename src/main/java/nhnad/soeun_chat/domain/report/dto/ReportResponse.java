package nhnad.soeun_chat.domain.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리포트 생성 응답")
public record ReportResponse(
        @Schema(description = "생성된 공유 토큰", example = "sh_eyJhbGci...")
        String shareToken,

        @Schema(description = "생성된 리포트의 대화 ID", example = "conv-report-20240311")
        String conversationId
) {}
