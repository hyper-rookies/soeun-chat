package nhnad.soeun_chat.domain.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Excel 내보내기 응답")
public record ExcelExportResponse(
        @Schema(description = "S3 Pre-signed 다운로드 URL (유효시간 약 15분)", example = "https://s3.amazonaws.com/se-report-ad-data/exports/report-20240311.xlsx?X-Amz-Expires=900&...")
        String downloadUrl,

        @Schema(description = "다운로드 파일명", example = "report-2024-03-11.xlsx")
        String fileName
) {}
